/* Copyright 2002-2014 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.propagation.semianalytical.dsst.forces;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathUtils;
import org.orekit.attitudes.AttitudeProvider;
import org.orekit.errors.OrekitException;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider;
import org.orekit.forces.gravity.potential.UnnormalizedSphericalHarmonicsProvider.UnnormalizedSphericalHarmonics;
import org.orekit.frames.Frame;
import org.orekit.frames.Transform;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.OrbitType;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.events.EventDetector;
import org.orekit.propagation.semianalytical.dsst.utilities.AuxiliaryElements;
import org.orekit.propagation.semianalytical.dsst.utilities.CoefficientsFactory;
import org.orekit.propagation.semianalytical.dsst.utilities.GHmsjPolynomials;
import org.orekit.propagation.semianalytical.dsst.utilities.GammaMnsFunction;
import org.orekit.propagation.semianalytical.dsst.utilities.JacobiPolynomials;
import org.orekit.propagation.semianalytical.dsst.utilities.ShortPeriodicsInterpolatedCoefficient;
import org.orekit.propagation.semianalytical.dsst.utilities.hansen.HansenTesseralLinear;
import org.orekit.time.AbsoluteDate;

/** Tesseral contribution to the {@link DSSTCentralBody central body gravitational
 *  perturbation}.
 *  <p>
 *  Only resonant tesserals are considered.
 *  </p>
 *
 *  @author Romain Di Costanzo
 *  @author Pascal Parraud
 */
class TesseralContribution implements DSSTForceModel {

    /** Minimum period for analytically averaged high-order resonant
     *  central body spherical harmonics in seconds.
     */
    private static final double MIN_PERIOD_IN_SECONDS = 864000.;

    /** Minimum period for analytically averaged high-order resonant
     *  central body spherical harmonics in satellite revolutions.
     */
    private static final double MIN_PERIOD_IN_SAT_REV = 10.;

    /** Number of points for interpolation. */
    private static final int INTERPOLATION_POINTS = 3;

    /** Provider for spherical harmonics. */
    private final UnnormalizedSphericalHarmonicsProvider provider;

    /** Central body rotating frame. */
    private final Frame bodyFrame;

    /** Central body rotation rate (rad/s). */
    private final double centralBodyRotationRate;

    /** Central body rotation period (seconds). */
    private final double bodyPeriod;

    /** Maximal degree to consider for harmonics potential. */
    private final int maxDegree;

    /** Maximal order to consider for harmonics potential. */
    private final int maxOrder;

    /** List of resonant orders. */
    private final List<Integer> resOrders;

    /** Factorial. */
    private final double[] fact;

    /** Maximum power of the eccentricity to use in summation over s. */
    private int maxEccPow;

    /** Maximum power of the eccentricity to use in Hansen coefficient Kernel expansion. */
    private int maxHansen;

    /** Keplerian period. */
    private double orbitPeriod;

    /** Ratio of satellite period to central body rotation period. */
    private double ratio;

    /** Retrograde factor. */
    private int    I;

    // Equinoctial elements (according to DSST notation)
    /** a. */
    private double a;

    /** ex. */
    private double k;

    /** ey. */
    private double h;

    /** hx. */
    private double q;

    /** hy. */
    private double p;

    /** lm. */
    private double lm;

    /** Eccentricity. */
    private double ecc;

    // Common factors for potential computation
    /** &Chi; = 1 / sqrt(1 - e<sup>2</sup>) = 1 / B. */
    private double chi;

    /** &Chi;<sup>2</sup>. */
    private double chi2;

    // Equinoctial reference frame vectors (according to DSST notation)
    /** Equinoctial frame f vector. */
    private Vector3D f;

    /** Equinoctial frame g vector. */
    private Vector3D g;

    /** Central body rotation angle &theta;. */
    private double theta;

    /** Direction cosine &alpha;. */
    private double alpha;

    /** Direction cosine &beta;. */
    private double beta;

    /** Direction cosine &gamma;. */
    private double gamma;

    // Common factors from equinoctial coefficients
    /** 2 * a / A .*/
    private double ax2oA;

    /** 1 / (A * B) .*/
    private double ooAB;

    /** B / A .*/
    private double BoA;

    /** B / (A * (1 + B)) .*/
    private double BoABpo;

    /** C / (2 * A * B) .*/
    private double Co2AB;

    /** &mu; / a .*/
    private double moa;

    /** R / a .*/
    private double roa;

    /** ecc<sup>2</sup>. */
    private double e2;

    /** Flag to take into account only M-dailies harmonic tesserals for short periodic perturbations.  */
    private boolean mDailiesOnly;

    /** Maximum value for j.
     * <p>
     * jmax = maxDegree + maxOrder
     * </p>
     * */
    private final int jMax;

    /** List of non resonant orders. */
    private SortedMap<Integer, List<Integer> > nonResOrders;

    /** A two dimensional array that contains the objects needed to build the Hansen coefficients. <br/>
     * The indexes are s + maxDegree and j */
    private HansenTesseralLinear[][] hansenObjects;

    /** The C<sub>i</sub><sup>j</sup><sup>m</sup> and S<sub>i</sub><sup>j</sup><sup>m</sup> coefficients
     * used to compute the short-periodic tesseral contribution. */
    private TesseralShortPeriodicCoefficients tesseralSPCoefs;

    /** The frame used to describe the orbits. */
    private Frame frame;

    /** Single constructor.
     *  @param centralBodyFrame rotating body frame
     *  @param centralBodyRotationRate central body rotation rate (rad/s)
     *  @param provider provider for spherical harmonics
     *  @param mDailiesOnly if true only M-dailies tesseral harmonics are taken into account for short periodics
     */
    public TesseralContribution(final Frame centralBodyFrame,
                                final double centralBodyRotationRate,
                                final UnnormalizedSphericalHarmonicsProvider provider,
                                final boolean mDailiesOnly) {

        // Central body rotating frame
        this.bodyFrame = centralBodyFrame;

        //Save the rotation rate
        this.centralBodyRotationRate = centralBodyRotationRate;

        // Central body rotation period in seconds
        this.bodyPeriod = MathUtils.TWO_PI / centralBodyRotationRate;

        // Provider for spherical harmonics
        this.provider  = provider;
        this.maxDegree = provider.getMaxDegree();
        this.maxOrder  = provider.getMaxOrder();
        this.jMax      = this.maxDegree + this.maxOrder;

        // m-daylies only
        this.mDailiesOnly = mDailiesOnly;

        // Initialize default values
        this.resOrders = new ArrayList<Integer>();
        this.nonResOrders = new TreeMap<Integer, List <Integer> >();
        this.maxEccPow = 0;
        this.maxHansen = 0;

       // Factorials computation
        final int maxFact = 2 * maxDegree + 1;
        this.fact = new double[maxFact];
        fact[0] = 1;
        for (int i = 1; i < maxFact; i++) {
            fact[i] = i * fact[i - 1];
        }
    }

    /** {@inheritDoc} */
    public void initialize(final AuxiliaryElements aux, final boolean meanOnly)
        throws OrekitException {

        // Keplerian period
        orbitPeriod = aux.getKeplerianPeriod();

        // orbit frame
        frame = aux.getFrame();

        // Ratio of satellite to central body periods to define resonant terms
        ratio = orbitPeriod / bodyPeriod;

        // Compute the resonant tesseral harmonic terms if not set by the user
        getResonantAndNonResonantTerms(meanOnly);

        // Set the highest power of the eccentricity in the analytical power
        // series expansion for the averaged high order resonant central body
        // spherical harmonic perturbation
        final double e = aux.getEcc();
        if (e <= 0.005) {
            maxEccPow = 3;
        } else if (e <= 0.02) {
            maxEccPow = 4;
        } else if (e <= 0.1) {
            maxEccPow = 7;
        } else if (e <= 0.2) {
            maxEccPow = 10;
        } else if (e <= 0.3) {
            maxEccPow = 12;
        } else if (e <= 0.4) {
            maxEccPow = 15;
        } else {
            maxEccPow = 20;
        }

        // Set the maximum power of the eccentricity to use in Hansen coefficient Kernel expansion.
        maxHansen = maxEccPow / 2;

        //initialize the HansenTesseralLinear objects needed
        createHansenObjects(meanOnly);

        if (!meanOnly) {
            //Initialize the Tesseral Short Periodics coefficient class
            tesseralSPCoefs = new TesseralShortPeriodicCoefficients(jMax,
                    maxOrder, INTERPOLATION_POINTS);
        }
    }

    /** Create the objects needed for linear transformation.
     *
     * <p>
     * Each {@link org.orekit.propagation.semianalytical.dsst.utilities.hansenHansenTesseralLinear HansenTesseralLinear} uses
     * a fixed value for s and j. Since j varies from -maxJ to +maxJ and s varies from -maxDegree to +maxDegree,
     * a 2 * maxDegree + 1 x 2 * maxJ + 1 matrix of objects should be created. The size of this matrix can be reduced
     * by taking into account the expression (2.7.3-2). This means that is enough to create the objects for  positive
     * values of j and all values of s.
     * </p>
     *
     * @param meanOnly create only the objects required for the mean contribution
     */
    private void createHansenObjects(final boolean meanOnly) {
        //Allocate the two dimensional array
        this.hansenObjects = new HansenTesseralLinear[2 * maxDegree + 1][jMax + 1];

        if (meanOnly) {
            // loop through the resonant orders
            for (int m : resOrders) {
                //Compute the corresponding j term
                final int j = FastMath.max(1, (int) FastMath.round(ratio * m));

                //Compute the sMin and sMax values
                final int sMin = FastMath.min(maxEccPow - j, maxDegree);
                final int sMax = FastMath.min(maxEccPow + j, maxDegree);

                //loop through the s values
                for (int s = 0; s <= sMax; s++) {
                    //Compute the n0 value
                    final int n0 = FastMath.max(FastMath.max(2, m), s);

                    //Create the object for the pair j,s
                    this.hansenObjects[s + maxDegree][j] = new HansenTesseralLinear(maxDegree, s, j, n0, maxHansen);

                    if (s > 0 && s <= sMin) {
                        //Also create the object for the pair j, -s
                        this.hansenObjects[maxDegree - s][j] =  new HansenTesseralLinear(maxDegree, -s, j, n0, maxHansen);
                    }
                }
            }
        } else {
            // create all objects
            for (int j = 0; j <= jMax; j++) {
                for (int s = -maxDegree; s <= maxDegree; s++) {
                    //Compute the n0 value
                    final int n0 = FastMath.max(2, FastMath.abs(s));

                    this.hansenObjects[s + maxDegree][j] = new HansenTesseralLinear(maxDegree, s, j, n0, maxHansen);
                }
            }
        }
    }

    /** {@inheritDoc} */
    public void initializeStep(final AuxiliaryElements aux) throws OrekitException {

        // Equinoctial elements
        a  = aux.getSma();
        k  = aux.getK();
        h  = aux.getH();
        q  = aux.getQ();
        p  = aux.getP();
        lm = aux.getLM();

        // Eccentricity
        ecc = aux.getEcc();
        e2 = ecc * ecc;

        // Retrograde factor
        I = aux.getRetrogradeFactor();

        // Equinoctial frame vectors
        f = aux.getVectorF();
        g = aux.getVectorG();

        // Central body rotation angle from equation 2.7.1-(3)(4).
        final Transform t = bodyFrame.getTransformTo(aux.getFrame(), aux.getDate());
        final Vector3D xB = t.transformVector(Vector3D.PLUS_I);
        final Vector3D yB = t.transformVector(Vector3D.PLUS_J);
        theta = FastMath.atan2(-f.dotProduct(yB) + I * g.dotProduct(xB),
                                f.dotProduct(xB) + I * g.dotProduct(yB));

        // Direction cosines
        alpha = aux.getAlpha();
        beta  = aux.getBeta();
        gamma = aux.getGamma();

        // Equinoctial coefficients
        // A = sqrt(&mu; * a)
        final double A = aux.getA();
        // B = sqrt(1 - h<sup>2</sup> - k<sup>2</sup>)
        final double B = aux.getB();
        // C = 1 + p<sup>2</sup> + q<sup>2</sup>
        final double C = aux.getC();
        // Common factors from equinoctial coefficients
        // 2 * a / A
        ax2oA  = 2. * a / A;
        // B / A
        BoA  = B / A;
        // 1 / AB
        ooAB = 1. / (A * B);
        // C / 2AB
        Co2AB = C * ooAB / 2.;
        // B / (A * (1 + B))
        BoABpo = BoA / (1. + B);
        // &mu / a
        moa = provider.getMu() / a;
        // R / a
        roa = provider.getAe() / a;

        // &Chi; = 1 / B
        chi = 1. / B;
        chi2 = chi * chi;
    }

    /** {@inheritDoc} */
    public double[] getMeanElementRate(final SpacecraftState spacecraftState) throws OrekitException {

        // Compute potential derivatives
        final double[] dU  = computeUDerivatives(spacecraftState.getDate());
        final double dUda  = dU[0];
        final double dUdh  = dU[1];
        final double dUdk  = dU[2];
        final double dUdl  = dU[3];
        final double dUdAl = dU[4];
        final double dUdBe = dU[5];
        final double dUdGa = dU[6];

        // Compute the cross derivative operator :
        final double UAlphaGamma   = alpha * dUdGa - gamma * dUdAl;
        final double UAlphaBeta    = alpha * dUdBe - beta  * dUdAl;
        final double UBetaGamma    =  beta * dUdGa - gamma * dUdBe;
        final double Uhk           =     h * dUdk  -     k * dUdh;
        final double pUagmIqUbgoAB = (p * UAlphaGamma - I * q * UBetaGamma) * ooAB;
        final double UhkmUabmdUdl  =  Uhk - UAlphaBeta - dUdl;

        final double da =  ax2oA * dUdl;
        final double dh =    BoA * dUdk + k * pUagmIqUbgoAB - h * BoABpo * dUdl;
        final double dk =  -(BoA * dUdh + h * pUagmIqUbgoAB + k * BoABpo * dUdl);
        final double dp =  Co2AB * (p * UhkmUabmdUdl - UBetaGamma);
        final double dq =  Co2AB * (q * UhkmUabmdUdl - I * UAlphaGamma);
        final double dM = -ax2oA * dUda + BoABpo * (h * dUdh + k * dUdk) + pUagmIqUbgoAB;

        return new double[] {da, dk, dh, dq, dp, dM};
    }

    /** {@inheritDoc} */
    public double[] getShortPeriodicVariations(final AbsoluteDate date,
                                               final double[] meanElements)
        throws OrekitException {

        // Initialise the short periodic variations
        final double[] shortPeriodicVariation = new double[] {0., 0., 0., 0., 0., 0.};

        // Compute only if there is at least one non-resonant tesseral
        if (!nonResOrders.isEmpty()) {

            // Build an Orbit object from the mean elements
            final Orbit meanOrbit = OrbitType.EQUINOCTIAL.mapArrayToOrbit(
                    meanElements, PositionAngle.MEAN, date, provider.getMu(), this.frame);

            //Build an auxiliary object
            final AuxiliaryElements aux = new AuxiliaryElements(meanOrbit, I);

            // Central body rotation angle from equation 2.7.1-(3)(4).
            final Transform t = bodyFrame.getTransformTo(aux.getFrame(), aux.getDate());
            final Vector3D xB = t.transformVector(Vector3D.PLUS_I);
            final Vector3D yB = t.transformVector(Vector3D.PLUS_J);
            final double currentTheta = FastMath.atan2(-f.dotProduct(yB) + I * g.dotProduct(xB),
                                                        f.dotProduct(xB) + I * g.dotProduct(yB));

            // loop through all non-resonant (j,m) pairs
            for (int m : nonResOrders.keySet()) {
                final List<Integer> listJ = nonResOrders.get(m);

                for (int j : listJ) {
                    // Phase angle
                    final double jlMmt  = j * meanElements[5] - m * currentTheta;
                    final double sinPhi = FastMath.sin(jlMmt);
                    final double cosPhi = FastMath.cos(jlMmt);

                    // compute contribution for each element
                    for (int i = 0; i < 6; i++) {
                        shortPeriodicVariation[i] += tesseralSPCoefs.getCijm(i, j, m, date) * cosPhi +
                                                     tesseralSPCoefs.getSijm(i, j, m, date) * sinPhi;
                    }
                }
            }
        }

        return shortPeriodicVariation;
    }

    /** {@inheritDoc} */
    public EventDetector[] getEventsDetectors() {
        return null;
    }

    /** {@inheritDoc} */
    public void computeShortPeriodicsCoefficients(final AuxiliaryElements aux) throws OrekitException {
        // Initialize internal fields
        initializeStep(aux);

        // Initialise the Hansen coefficients
        for (int j = 0; j <= jMax; j++) {
            for (int s = -maxDegree; s <= maxDegree; s++) {
                this.hansenObjects[s + maxDegree][j].computeInitValues(e2, chi, chi2);
            }
        }

        // Compute coefficients
        tesseralSPCoefs.computeCoefficients(aux);
    }

    /** Get the resonant and non-resonant tesseral terms in the central body spherical harmonic field.
     *
     * @param resonantOnly extract only resonant terms
     */
    private void getResonantAndNonResonantTerms(final boolean resonantOnly) {


        // Compute natural resonant terms
        final double tolerance = 1. / FastMath.max(MIN_PERIOD_IN_SAT_REV,
                                                   MIN_PERIOD_IN_SECONDS / orbitPeriod);

        // Search the resonant orders in the tesseral harmonic field
        resOrders.clear();
        nonResOrders.clear();
        for (int m = 1; m <= maxOrder; m++) {
            final double resonance = ratio * m;
            int jRes = 0;
            final int jComputedRes = (int) FastMath.round(resonance);
            if (jComputedRes > 0 && FastMath.abs(resonance - jComputedRes) <= tolerance) {
                // Store each resonant index and order
                resOrders.add(m);
                jRes = jComputedRes;
            }

            if (!resonantOnly) {
                //compute non resonant orders in the tesseral harmonic field
                final List<Integer> listJofM = new ArrayList<Integer>();
                if (mDailiesOnly) {
                    //for M-dailies we have j = 0 only in the (j,m) pairs
                    listJofM.add(0);
                } else {
                    //for the moment we take only the pairs (j,m) with |j| <= maxDegree + maxEccPow (from |s-j| <= maxEccPow and |s| <= maxDegree)
                    for (int j = -jMax; j <= jMax; j++) {
                        if (jRes == 0 || j != jRes) {
                            listJofM.add(j);
                        }
                    }
                }
                nonResOrders.put(m, listJofM);
            }
        }
    }

    /** Computes the potential U derivatives.
     *  <p>The following elements are computed from expression 3.3 - (4).
     *  <pre>
     *  dU / da
     *  dU / dh
     *  dU / dk
     *  dU / d&lambda;
     *  dU / d&alpha;
     *  dU / d&beta;
     *  dU / d&gamma;
     *  </pre>
     *  </p>
     *
     *  @param date current date
     *  @return potential derivatives
     *  @throws OrekitException if an error occurs
     */
    private double[] computeUDerivatives(final AbsoluteDate date) throws OrekitException {

        // Potential derivatives
        double dUda  = 0.;
        double dUdh  = 0.;
        double dUdk  = 0.;
        double dUdl  = 0.;
        double dUdAl = 0.;
        double dUdBe = 0.;
        double dUdGa = 0.;

        // Compute only if there is at least one resonant tesseral
        if (!resOrders.isEmpty()) {
            // Gmsj and Hmsj polynomials
            final GHmsjPolynomials ghMSJ = new GHmsjPolynomials(k, h, alpha, beta, I);

            // GAMMAmns function
            final GammaMnsFunction gammaMNS = new GammaMnsFunction(fact, gamma, I);

            // R / a up to power degree
            final double[] roaPow = new double[maxDegree + 1];
            roaPow[0] = 1.;
            for (int i = 1; i <= maxDegree; i++) {
                roaPow[i] = roa * roaPow[i - 1];
            }

            // SUM over resonant terms {j,m}
            for (int m : resOrders) {

                // Resonant index for the current resonant order
                final int j = FastMath.max(1, (int) FastMath.round(ratio * m));

                // Phase angle
                final double jlMmt  = j * lm - m * theta;
                final double sinPhi = FastMath.sin(jlMmt);
                final double cosPhi = FastMath.cos(jlMmt);

                // Potential derivatives components for a given resonant pair {j,m}
                double dUdaCos  = 0.;
                double dUdaSin  = 0.;
                double dUdhCos  = 0.;
                double dUdhSin  = 0.;
                double dUdkCos  = 0.;
                double dUdkSin  = 0.;
                double dUdlCos  = 0.;
                double dUdlSin  = 0.;
                double dUdAlCos = 0.;
                double dUdAlSin = 0.;
                double dUdBeCos = 0.;
                double dUdBeSin = 0.;
                double dUdGaCos = 0.;
                double dUdGaSin = 0.;

                // s-SUM from -sMin to sMax
                final int sMin = FastMath.min(maxEccPow - j, maxDegree);
                final int sMax = FastMath.min(maxEccPow + j, maxDegree);
                for (int s = 0; s <= sMax; s++) {

                    //Compute the initial values for Hansen coefficients using newComb operators
                    this.hansenObjects[s + maxDegree][j].computeInitValues(e2, chi, chi2);

                    // n-SUM for s positive
                    final double[][] nSumSpos = computeNSum(date, j, m, s,
                                                            roaPow, ghMSJ, gammaMNS);
                    dUdaCos  += nSumSpos[0][0];
                    dUdaSin  += nSumSpos[0][1];
                    dUdhCos  += nSumSpos[1][0];
                    dUdhSin  += nSumSpos[1][1];
                    dUdkCos  += nSumSpos[2][0];
                    dUdkSin  += nSumSpos[2][1];
                    dUdlCos  += nSumSpos[3][0];
                    dUdlSin  += nSumSpos[3][1];
                    dUdAlCos += nSumSpos[4][0];
                    dUdAlSin += nSumSpos[4][1];
                    dUdBeCos += nSumSpos[5][0];
                    dUdBeSin += nSumSpos[5][1];
                    dUdGaCos += nSumSpos[6][0];
                    dUdGaSin += nSumSpos[6][1];

                    // n-SUM for s negative
                    if (s > 0 && s <= sMin) {
                        //Compute the initial values for Hansen coefficients using newComb operators
                        this.hansenObjects[maxDegree - s][j].computeInitValues(e2, chi, chi2);

                        final double[][] nSumSneg = computeNSum(date, j, m, -s,
                                                                roaPow, ghMSJ, gammaMNS);
                        dUdaCos  += nSumSneg[0][0];
                        dUdaSin  += nSumSneg[0][1];
                        dUdhCos  += nSumSneg[1][0];
                        dUdhSin  += nSumSneg[1][1];
                        dUdkCos  += nSumSneg[2][0];
                        dUdkSin  += nSumSneg[2][1];
                        dUdlCos  += nSumSneg[3][0];
                        dUdlSin  += nSumSneg[3][1];
                        dUdAlCos += nSumSneg[4][0];
                        dUdAlSin += nSumSneg[4][1];
                        dUdBeCos += nSumSneg[5][0];
                        dUdBeSin += nSumSneg[5][1];
                        dUdGaCos += nSumSneg[6][0];
                        dUdGaSin += nSumSneg[6][1];
                    }
                }

                // Assembly of potential derivatives componants
                dUda  += cosPhi * dUdaCos  + sinPhi * dUdaSin;
                dUdh  += cosPhi * dUdhCos  + sinPhi * dUdhSin;
                dUdk  += cosPhi * dUdkCos  + sinPhi * dUdkSin;
                dUdl  += cosPhi * dUdlCos  + sinPhi * dUdlSin;
                dUdAl += cosPhi * dUdAlCos + sinPhi * dUdAlSin;
                dUdBe += cosPhi * dUdBeCos + sinPhi * dUdBeSin;
                dUdGa += cosPhi * dUdGaCos + sinPhi * dUdGaSin;
            }

            dUda  *= -moa / a;
            dUdh  *=  moa;
            dUdk  *=  moa;
            dUdl  *=  moa;
            dUdAl *=  moa;
            dUdBe *=  moa;
            dUdGa *=  moa;
        }

        return new double[] {dUda, dUdh, dUdk, dUdl, dUdAl, dUdBe, dUdGa};
    }

    /** Compute the n-SUM for potential derivatives components.
     *  @param date current date
     *  @param j resonant index <i>j</i>
     *  @param m resonant order <i>m</i>
     *  @param s d'Alembert characteristic <i>s</i>
     *  @param roaPow powers of R/a up to degree <i>n</i>
     *  @param ghMSJ G<sup>j</sup><sub>m,s</sub> and H<sup>j</sup><sub>m,s</sub> polynomials
     *  @param gammaMNS &Gamma;<sup>m</sup><sub>n,s</sub>(&gamma;) function
     *  @return Components of U<sub>n</sub> derivatives for fixed j, m, s
     * @throws OrekitException if some error occurred
     */
    private double[][] computeNSum(final AbsoluteDate date,
                                   final int j, final int m, final int s, final double[] roaPow,
                                   final GHmsjPolynomials ghMSJ, final GammaMnsFunction gammaMNS)
        throws OrekitException {

        //spherical harmonics
        final UnnormalizedSphericalHarmonics harmonics = provider.onDate(date);

        // Potential derivatives components
        double dUdaCos  = 0.;
        double dUdaSin  = 0.;
        double dUdhCos  = 0.;
        double dUdhSin  = 0.;
        double dUdkCos  = 0.;
        double dUdkSin  = 0.;
        double dUdlCos  = 0.;
        double dUdlSin  = 0.;
        double dUdAlCos = 0.;
        double dUdAlSin = 0.;
        double dUdBeCos = 0.;
        double dUdBeSin = 0.;
        double dUdGaCos = 0.;
        double dUdGaSin = 0.;

        // I^m
        final int Im = I > 0 ? 1 : (m % 2 == 0 ? 1 : -1);

        // jacobi v, w, indices from 2.7.1-(15)
        final int v = FastMath.abs(m - s);
        final int w = FastMath.abs(m + s);

        // Initialise lower degree nmin = (Max(2, m, |s|)) for summation over n
        final int nmin = FastMath.max(FastMath.max(2, m), FastMath.abs(s));

        //Get the corresponding Hansen object
        final int sIndex = maxDegree + (j < 0 ? -s : s);
        final int jIndex = FastMath.abs(j);
        final HansenTesseralLinear hans = this.hansenObjects[sIndex][jIndex];

        // n-SUM from nmin to N
        for (int n = nmin; n <= maxDegree; n++) {
            // If (n - s) is odd, the contribution is null because of Vmns
            if ((n - s) % 2 == 0) {

                // Vmns coefficient
                final double fns    = fact[n + FastMath.abs(s)];
                final double vMNS   = CoefficientsFactory.getVmns(m, n, s, fns, fact[n - m]);

                // Inclination function Gamma and derivative
                final double gaMNS  = gammaMNS.getValue(m, n, s);
                final double dGaMNS = gammaMNS.getDerivative(m, n, s);

                // Hansen kernel value and derivative
                final double kJNS   = hans.getValue(-n - 1, chi);
                final double dkJNS  = hans.getDerivative(-n - 1, chi);

                // Gjms, Hjms polynomials and derivatives
                final double gMSJ   = ghMSJ.getGmsj(m, s, j);
                final double hMSJ   = ghMSJ.getHmsj(m, s, j);
                final double dGdh   = ghMSJ.getdGmsdh(m, s, j);
                final double dGdk   = ghMSJ.getdGmsdk(m, s, j);
                final double dGdA   = ghMSJ.getdGmsdAlpha(m, s, j);
                final double dGdB   = ghMSJ.getdGmsdBeta(m, s, j);
                final double dHdh   = ghMSJ.getdHmsdh(m, s, j);
                final double dHdk   = ghMSJ.getdHmsdk(m, s, j);
                final double dHdA   = ghMSJ.getdHmsdAlpha(m, s, j);
                final double dHdB   = ghMSJ.getdHmsdBeta(m, s, j);

                // Jacobi l-index from 2.7.1-(15)
                final int l = FastMath.min(n - m, n - FastMath.abs(s));
                // Jacobi polynomial and derivative
                final DerivativeStructure jacobi =
                        JacobiPolynomials.getValue(l, v , w, new DerivativeStructure(1, 1, 0, gamma));

                // Geopotential coefficients
                final double cnm = harmonics.getUnnormalizedCnm(n, m);
                final double snm = harmonics.getUnnormalizedSnm(n, m);

                // Common factors from expansion of equations 3.3-4
                final double cf_0      = roaPow[n] * Im * vMNS;
                final double cf_1      = cf_0 * gaMNS * jacobi.getValue();
                final double cf_2      = cf_1 * kJNS;
                final double gcPhs     = gMSJ * cnm + hMSJ * snm;
                final double gsMhc     = gMSJ * snm - hMSJ * cnm;
                final double dKgcPhsx2 = 2. * dkJNS * gcPhs;
                final double dKgsMhcx2 = 2. * dkJNS * gsMhc;
                final double dUdaCoef  = (n + 1) * cf_2;
                final double dUdlCoef  = j * cf_2;
                final double dUdGaCoef = cf_0 * kJNS * (jacobi.getValue() * dGaMNS + gaMNS * jacobi.getPartialDerivative(1));

                // dU / da components
                dUdaCos  += dUdaCoef * gcPhs;
                dUdaSin  += dUdaCoef * gsMhc;

                // dU / dh components
                dUdhCos  += cf_1 * (kJNS * (cnm * dGdh + snm * dHdh) + h * dKgcPhsx2);
                dUdhSin  += cf_1 * (kJNS * (snm * dGdh - cnm * dHdh) + h * dKgsMhcx2);

                // dU / dk components
                dUdkCos  += cf_1 * (kJNS * (cnm * dGdk + snm * dHdk) + k * dKgcPhsx2);
                dUdkSin  += cf_1 * (kJNS * (snm * dGdk - cnm * dHdk) + k * dKgsMhcx2);

                // dU / dLambda components
                dUdlCos  +=  dUdlCoef * gsMhc;
                dUdlSin  += -dUdlCoef * gcPhs;

                // dU / alpha components
                dUdAlCos += cf_2 * (dGdA * cnm + dHdA * snm);
                dUdAlSin += cf_2 * (dGdA * snm - dHdA * cnm);

                // dU / dBeta components
                dUdBeCos += cf_2 * (dGdB * cnm + dHdB * snm);
                dUdBeSin += cf_2 * (dGdB * snm - dHdB * cnm);

                // dU / dGamma components
                dUdGaCos += dUdGaCoef * gcPhs;
                dUdGaSin += dUdGaCoef * gsMhc;
            }
        }

        return new double[][] {{dUdaCos,  dUdaSin},
                               {dUdhCos,  dUdhSin},
                               {dUdkCos,  dUdkSin},
                               {dUdlCos,  dUdlSin},
                               {dUdAlCos, dUdAlSin},
                               {dUdBeCos, dUdBeSin},
                               {dUdGaCos, dUdGaSin}};
    }

    /** {@inheritDoc} */
    @Override
    public void registerAttitudeProvider(final AttitudeProvider attitudeProvider) {
        //nothing is done since this contribution is not sensitive to attitude
    }

    @Override
    public void resetShortPeriodicsCoefficients() {
        tesseralSPCoefs.resetCoefficients();
    }



    /** Compute the C<sup>j</sup> and the S<sup>j</sup> coefficients.
     *  <p>
     *  Those coefficients are given in Danielson paper by substituting the
     *  disturbing function (2.7.1-16) with m != 0 into (2.2-10)
     *  </p>
     */
    public class FourierCjSjCoefficients {

        /** Absolute limit for j ( -jMax <= j <= jMax ).  */
        private int jMax;

        /** The C<sub>i</sub><sup>jm</sup> coefficients.
         * <p>
         * The index order is [m][j][i] <br/>
         * The i index corresponds to the C<sub>i</sub><sup>jm</sup> coefficients used to
         * compute the following: <br/>
         * - da/dt <br/>
         * - dk/dt <br/>
         * - dh/dt / dk <br/>
         * - dq/dt <br/>
         * - dp/dt / d&alpha; <br/>
         * - d&lambda;/dt / d&beta; <br/>
         * </p>
         */
        private final double[][][] cCoef;

        /** The S<sub>i</sub><sup>jm</sup> coefficients.
         * <p>
         * The index order is [m][j][i] <br/>
         * The i index corresponds to the C<sub>i</sub><sup>jm</sup> coefficients used to
         * compute the following: <br/>
         * - da/dt <br/>
         * - dk/dt <br/>
         * - dh/dt / dk <br/>
         * - dq/dt <br/>
         * - dp/dt / d&alpha; <br/>
         * - d&lambda;/dt / d&beta; <br/>
         * </p>
         */
        private final double[][][] sCoef;

        /** Create a set of C<sub>i</sub><sup>jm</sup> and S<sub>i</sub><sup>jm</sup> coefficients.
         *  @param aux The auxiliary elements
         *  @param jMax absolute limit for j ( -jMax <= j <= jMax )
         *  @param mMax maximum value for m
         *  @throws OrekitException if an error occurs while generating the coefficients
         */
        public FourierCjSjCoefficients(final AuxiliaryElements aux, final int jMax, final int mMax)
            throws OrekitException {

            this.jMax = jMax;
            this.cCoef = new double[mMax + 1][2 * jMax + 1][6];
            this.sCoef = new double[mMax + 1][2 * jMax + 1][6];

            generateCoefficients(aux);
        }

        /**
         * Generate the coefficients.
         * @param aux The auxiliary elements
         * @throws OrekitException if an error occurs while generating the coefficients
         */
        private void generateCoefficients(final AuxiliaryElements aux) throws OrekitException {
            // Compute only if there is at least one non-resonant tesseral
            if (!nonResOrders.isEmpty()) {
                // Gmsj and Hmsj polynomials
                final GHmsjPolynomials ghMSJ = new GHmsjPolynomials(k, h, alpha, beta, I);

                // GAMMAmns function
                final GammaMnsFunction gammaMNS = new GammaMnsFunction(fact, gamma, I);

                // R / a up to power degree
                final double[] roaPow = new double[maxDegree + 1];
                roaPow[0] = 1.;
                for (int i = 1; i <= maxDegree; i++) {
                    roaPow[i] = roa * roaPow[i - 1];
                }

                // the date
                final AbsoluteDate date = aux.getDate();

                // generate the coefficients
                for (int m: nonResOrders.keySet()) {
                    final List<Integer> listJ = nonResOrders.get(m);

                    for (int j: listJ) {

                        // Potential derivatives components for a given non-resonant pair {j,m}
                        double dRdaCos  = 0.;
                        double dRdaSin  = 0.;
                        double dRdhCos  = 0.;
                        double dRdhSin  = 0.;
                        double dRdkCos  = 0.;
                        double dRdkSin  = 0.;
                        double dRdlCos  = 0.;
                        double dRdlSin  = 0.;
                        double dRdAlCos = 0.;
                        double dRdAlSin = 0.;
                        double dRdBeCos = 0.;
                        double dRdBeSin = 0.;
                        double dRdGaCos = 0.;
                        double dRdGaSin = 0.;

                        // s-SUM from -sMin to sMax
                        final int sMin = FastMath.min(maxEccPow - j, maxDegree);
                        final int sMax = FastMath.min(maxEccPow + j, maxDegree);
                        for (int s = 0; s <= sMax; s++) {

                            // n-SUM for s positive
                            final double[][] nSumSpos = computeNSum(date, j, m, s,
                                                                    roaPow, ghMSJ, gammaMNS);
                            dRdaCos  += nSumSpos[0][0];
                            dRdaSin  += nSumSpos[0][1];
                            dRdhCos  += nSumSpos[1][0];
                            dRdhSin  += nSumSpos[1][1];
                            dRdkCos  += nSumSpos[2][0];
                            dRdkSin  += nSumSpos[2][1];
                            dRdlCos  += nSumSpos[3][0];
                            dRdlSin  += nSumSpos[3][1];
                            dRdAlCos += nSumSpos[4][0];
                            dRdAlSin += nSumSpos[4][1];
                            dRdBeCos += nSumSpos[5][0];
                            dRdBeSin += nSumSpos[5][1];
                            dRdGaCos += nSumSpos[6][0];
                            dRdGaSin += nSumSpos[6][1];

                            // n-SUM for s negative
                            if (s > 0 && s <= sMin) {
                                final double[][] nSumSneg = computeNSum(date, j, m, -s,
                                                                        roaPow, ghMSJ, gammaMNS);
                                dRdaCos  += nSumSneg[0][0];
                                dRdaSin  += nSumSneg[0][1];
                                dRdhCos  += nSumSneg[1][0];
                                dRdhSin  += nSumSneg[1][1];
                                dRdkCos  += nSumSneg[2][0];
                                dRdkSin  += nSumSneg[2][1];
                                dRdlCos  += nSumSneg[3][0];
                                dRdlSin  += nSumSneg[3][1];
                                dRdAlCos += nSumSneg[4][0];
                                dRdAlSin += nSumSneg[4][1];
                                dRdBeCos += nSumSneg[5][0];
                                dRdBeSin += nSumSneg[5][1];
                                dRdGaCos += nSumSneg[6][0];
                                dRdGaSin += nSumSneg[6][1];
                            }
                        }
                        dRdaCos  *= -moa / a;
                        dRdaSin  *= -moa / a;
                        dRdhCos  *=  moa;
                        dRdhSin  *=  moa;
                        dRdkCos  *=  moa;
                        dRdkSin *=  moa;
                        dRdlCos *=  moa;
                        dRdlSin *=  moa;
                        dRdAlCos *=  moa;
                        dRdAlSin *=  moa;
                        dRdBeCos *=  moa;
                        dRdBeSin *=  moa;
                        dRdGaCos *=  moa;
                        dRdGaSin *=  moa;

                        // Compute the cross derivative operator :
                        final double RAlphaGammaCos   = alpha * dRdGaCos - gamma * dRdAlCos;
                        final double RAlphaGammaSin   = alpha * dRdGaSin - gamma * dRdAlSin;
                        final double RAlphaBetaCos    = alpha * dRdBeCos - beta  * dRdAlCos;
                        final double RAlphaBetaSin    = alpha * dRdBeSin - beta  * dRdAlSin;
                        final double RBetaGammaCos    =  beta * dRdGaCos - gamma * dRdBeCos;
                        final double RBetaGammaSin    =  beta * dRdGaSin - gamma * dRdBeSin;
                        final double RhkCos           =     h * dRdkCos  -     k * dRdhCos;
                        final double RhkSin           =     h * dRdkSin  -     k * dRdhSin;
                        final double pRagmIqRbgoABCos = (p * RAlphaGammaCos - I * q * RBetaGammaCos) * ooAB;
                        final double pRagmIqRbgoABSin = (p * RAlphaGammaSin - I * q * RBetaGammaSin) * ooAB;
                        final double RhkmRabmdRdlCos  =  RhkCos - RAlphaBetaCos - dRdlCos;
                        final double RhkmRabmdRdlSin  =  RhkSin - RAlphaBetaSin - dRdlSin;

                        // da/dt
                        cCoef[m][j + jMax][0] = ax2oA * dRdlCos;
                        sCoef[m][j + jMax][0] = ax2oA * dRdlSin;

                        // dk/dt
                        cCoef[m][j + jMax][1] = -(BoA * dRdhCos + h * pRagmIqRbgoABCos + k * BoABpo * dRdlCos);
                        sCoef[m][j + jMax][0] = -(BoA * dRdhSin + h * pRagmIqRbgoABSin + k * BoABpo * dRdlSin);

                        // dh/dt
                        cCoef[m][j + jMax][2] = BoA * dRdkCos + k * pRagmIqRbgoABCos - h * BoABpo * dRdlCos;
                        sCoef[m][j + jMax][2] = BoA * dRdkSin + k * pRagmIqRbgoABSin - h * BoABpo * dRdlSin;

                        // dq/dt
                        cCoef[m][j + jMax][3] = Co2AB * (q * RhkmRabmdRdlCos - I * RAlphaGammaCos);
                        sCoef[m][j + jMax][3] = Co2AB * (q * RhkmRabmdRdlSin - I * RAlphaGammaSin);

                        // dp/dt
                        cCoef[m][j + jMax][4] = Co2AB * (p * RhkmRabmdRdlCos - RBetaGammaCos);
                        sCoef[m][j + jMax][4] = Co2AB * (p * RhkmRabmdRdlSin - RBetaGammaSin);

                        // d&lambda;/dt
                        cCoef[m][j + jMax][5] = -ax2oA * dRdaCos + BoABpo * (h * dRdhCos + k * dRdkCos) + pRagmIqRbgoABCos;
                        sCoef[m][j + jMax][5] = -ax2oA * dRdaSin + BoABpo * (h * dRdhSin + k * dRdkSin) + pRagmIqRbgoABSin;
                    }
                }
            }
        }

        /** Get the coefficient C<sub>i</sub><sup>jm</sup>.
         * @param i i index - corresponds to the required variation
         * @param j j index
         * @param m m index
         * @return the coefficient C<sub>i</sub><sup>jm</sup>
         */
        public double getCijm(final int i, final int j, final int m) {
            return cCoef[m][j + jMax][i];
        }

        /** Get the coefficient S<sub>i</sub><sup>jm</sup>.
         * @param i i index - corresponds to the required variation
         * @param j j index
         * @param m m index
         * @return the coefficient S<sub>i</sub><sup>jm</sup>
         */
        public double getSijm(final int i, final int j, final int m) {
            return sCoef[m][j + jMax][i];
        }
    }

    /** The C<sup>i</sup><sub>m</sub><sub>t</sub> and S<sup>i</sup><sub>m</sub><sub>t</sub> coefficients used to compute
     * the short-periodic zonal contribution.
     *   <p>
     *  Those coefficients are given by expression 2.5.4-5 from the Danielsno paper.
     *   </p>
     *
     * @author Sorin Scortan
     *
     */
    private class TesseralShortPeriodicCoefficients {

        /** The coefficients C<sub>i</sub><sup>j</sup><sup>m</sup>.
         * <p>
         * The index order is cijm[m][j][i] <br/>
         * i corresponds to the equinoctial element, as follows: <br/>
         * - i=0 for a <br/>
         * - i=1 for k <br/>
         * - i=2 for h <br/>
         * - i=3 for q <br/>
         * - i=4 for p <br/>
         * - i=5 for &lambda; <br/>
         * </p>
         */
        private ShortPeriodicsInterpolatedCoefficient[][][] cijm;

        /** The coefficients S<sub>i</sub><sup>j</sup><sup>m</sup>.
         * <p>
         * The index order is sijm[m][j][i] <br/>
         * i corresponds to the equinoctial element, as follows: <br/>
         * - i=0 for a <br/>
         * - i=1 for k <br/>
         * - i=2 for h <br/>
         * - i=3 for q <br/>
         * - i=4 for p <br/>
         * - i=5 for &lambda; <br/>
         * </p>
         */
        private ShortPeriodicsInterpolatedCoefficient[][][] sijm;

        /** Absolute limit for j ( -jMax <= j <= jMax ).  */
        private int jMax;

        /** Maximum value for m.  */
        private int mMax;

        /** Constructor.
         * @param jMax absolute limit for j ( -jMax <= j <= jMax )
         * @param mMax maximum value for m
         * @param interpolationPoints number of points used in the interpolation process
         */
        public TesseralShortPeriodicCoefficients(final int jMax, final int mMax, final int interpolationPoints) {

            // Initialize fields
            this.jMax = jMax;
            this.mMax = mMax;
            this.cijm = new ShortPeriodicsInterpolatedCoefficient[mMax + 1][2 * jMax + 1][6];
            this.sijm = new ShortPeriodicsInterpolatedCoefficient[mMax + 1][2 * jMax + 1][6];

            for (int m = 1; m <= mMax; m++) {
                for (int j = -jMax; j <= jMax; j++) {
                    for (int i = 0; i < 6; i++) {
                        this.cijm[m][j + jMax][i] = new ShortPeriodicsInterpolatedCoefficient(interpolationPoints);
                        this.sijm[m][j + jMax][i] = new ShortPeriodicsInterpolatedCoefficient(interpolationPoints);
                    }
                }
            }
        }

        /** Compute the short periodic coefficients.
         *
         * @param aux the auxiliary data.
         * @throws OrekitException if an error occurs
         */
        public void computeCoefficients(final AuxiliaryElements aux)
            throws OrekitException {
            // Compute only if there is at least one non-resonant tesseral
            if (!nonResOrders.isEmpty()) {
                // the date
                final AbsoluteDate date = aux.getDate();

                // Create the fourrier coefficients
                final FourierCjSjCoefficients cjsjFourier = new FourierCjSjCoefficients(aux, jMax, mMax);

                // the coefficient 3n / 2a
                final double coef1 = 1.5 * orbitPeriod / a;

                // generate the coefficients
                for (int m: nonResOrders.keySet()) {
                    final List<Integer> listJ = nonResOrders.get(m);

                    for (int j: listJ) {
                        // Create local arrays
                        final double[] currentCijm = new double[] {0., 0., 0., 0., 0., 0.};
                        final double[] currentSijm = new double[] {0., 0., 0., 0., 0., 0.};

                        // compute the term 1 / (jn - m&theta;<sup>.</sup>)
                        final double oojnmt = 1. / (j * orbitPeriod + m * centralBodyRotationRate);

                        // initialise the coeficients
                        for (int i = 0; i < 6; i++) {
                            currentCijm[i] = -cjsjFourier.getSijm(i, j, m);
                            currentSijm[i] = cjsjFourier.getCijm(i, j, m);
                        }
                        // Add the separate part for &delta;<sub>6i</sub>
                        currentCijm[5] += coef1 * oojnmt * cjsjFourier.getCijm(0, j, m);
                        currentSijm[5] += coef1 * oojnmt * cjsjFourier.getSijm(0, j, m);

                        //Multiply by 1 / (jn - m&theta;<sup>.</sup>)
                        for (int i = 0; i < 6; i++) {
                            currentCijm[i] *= oojnmt;
                            currentSijm[i] *= oojnmt;
                        }

                        // Add the coefficients to the interpolation grid
                        for (int i = 0; i < 6; i++) {
                            cijm[m][j + jMax][i].addGridPoint(date, currentCijm[i]);
                            sijm[m][j + jMax][i].addGridPoint(date, currentSijm[i]);
                        }
                    }
                }
            }

        }

        /** Reset the coefficients.
         * <p>
         * For each coefficient, clear history of computed points
         * </p>
         */
        public void resetCoefficients() {
            for (int m = 1; m <= mMax; m++) {
                for (int j = -jMax; j <= jMax; j++) {
                    for (int i = 0; i < 6; i++) {
                        this.cijm[m][j + jMax][i].clearHistory();
                        this.sijm[m][j + jMax][i].clearHistory();
                    }
                }
            }
        }

        /** Get C<sub>i</sub><sup>j</sup><sup>m</sup>.
         *
         * @param i i index
         * @param j j index
         * @param m m index
         * @param date the date
         * @return C<sub>i</sub><sup>j</sup><sup>m</sup>
         */
        public double getCijm(final int i, final int j, final int m, final AbsoluteDate date) {
            return cijm[m][j + jMax][i].value(date);
        }

        /** Get S<sub>i</sub><sup>j</sup><sup>m</sup>.
         *
         * @param i i index
         * @param j j index
         * @param m m index
         * @param date the date
         * @return S<sub>i</sub><sup>j</sup><sup>m</sup>
         */
        public double getSijm(final int i, final int j, final int m, final AbsoluteDate date) {
            return sijm[m][j + jMax][i].value(date);
        }
    }
}
