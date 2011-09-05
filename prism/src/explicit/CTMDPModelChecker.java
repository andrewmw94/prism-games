//==============================================================================
//	
//	Copyright (c) 2002-
//	Authors:
//	* Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	
//------------------------------------------------------------------------------
//	
//	This file is part of PRISM.
//	
//	PRISM is free software; you can redistribute it and/or modify
//	it under the terms of the GNU General Public License as published by
//	the Free Software Foundation; either version 2 of the License, or
//	(at your option) any later version.
//	
//	PRISM is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//	GNU General Public License for more details.
//	
//	You should have received a copy of the GNU General Public License
//	along with PRISM; if not, write to the Free Software Foundation,
//	Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//	
//==============================================================================

package explicit;

import java.util.BitSet;
import java.util.Map;

import parser.ast.ExpressionTemporal;
import prism.PrismException;

/**
 * Explicit-state model checker for continuous-time Markov decision processes (CTMDPs).
 * 
 * This uses various bits of functionality of MDPModelChecker, so we inherit from that.
 * (This way MDPModelChecker picks up any options set on this one.) 
 */
public class CTMDPModelChecker extends MDPModelChecker
{
	protected StateValues checkProbBoundedUntil(Model model, ExpressionTemporal expr, boolean min) throws PrismException
	{
		double uTime;
		BitSet b1, b2;
		StateValues probs = null;
		ModelCheckerResult res = null;

		// get info from bounded until
		uTime = expr.getUpperBound().evaluateDouble(constantValues);
		if (uTime < 0 || (uTime == 0 && expr.upperBoundIsStrict())) {
			String bound = (expr.upperBoundIsStrict() ? "<" : "<=") + uTime;
			throw new PrismException("Invalid upper bound " + bound + " in time-bounded until formula");
		}

		// model check operands first
		b1 = checkExpression(model, expr.getOperand1()).getBitSet();
		b2 = checkExpression(model, expr.getOperand2()).getBitSet();

		// compute probabilities

		// a trivial case: "U<=0"
		if (uTime == 0) {
			// prob is 1 in b2 states, 0 otherwise
			probs = StateValues.createFromBitSetAsDoubles(model.getNumStates(), b2);
		} else {
			res = computeBoundedUntilProbs((CTMDP) model, b1, b2, uTime, min);
			probs = StateValues.createFromDoubleArray(res.soln);
		}

		return probs;
	}

	/**
	 * Compute bounded until probabilities.
	 * i.e. compute the min/max probability of reaching a state in {@code target},
	 * within time t, and while remaining in states in @{code remain}.
	 * @param ctmdp The CTMDP
	 * @param remain Remain in these states (optional: null means "all")
	 * @param target Target states
	 * @param t Bound
	 * @param min Min or max probabilities (true=min, false=max)
	 */
	public ModelCheckerResult computeBoundedUntilProbs(CTMDP ctmdp, BitSet remain, BitSet target, double t, boolean min) throws PrismException
	{
		return computeBoundedReachProbs(ctmdp, remain, target, t, min, null, null);
	}

	/**
	 * Compute bounded probabilistic reachability.
	 * @param ctmdp The CTMDP
	 * @param remain Remain in these states (optional: null means "all")
	 * @param target Target states
	 * @param t Time bound
	 * @param min Min or max probabilities for (true=min, false=max)
	 * @param init Initial solution vector - pass null for default
	 * @param results Optional array of size b+1 to store (init state) results for each step (null if unused)
	 */
	public ModelCheckerResult computeBoundedReachProbs(CTMDP ctmdp, BitSet remain, BitSet target, double t, boolean min, double init[], double results[]) throws PrismException
	{
		// TODO: implement until
		
		MDP mdp;
		MDPModelChecker mc;
		ModelCheckerResult res;

		if (!ctmdp.isLocallyUniform())
			throw new PrismException("Can't compute bounded reachability probabilities for non-locally uniform CTMDP");
		// TODO: check locally uniform
		double epsilon = 1e-3;
		double q = ctmdp.getMaxExitRate();
		int k = (int) Math.ceil((q * t * q * t) / (2 * epsilon));
		double tau = t / k;
		mainLog.println("q = " + q + ", k = " + k + ", tau = " + tau);
		mdp = ctmdp.buildDiscretisedMDP(tau);
		mainLog.println(mdp);
		mc = new MDPModelChecker();
		mc.inheritSettings(this);
		res = mc.computeBoundedUntilProbs(mdp, null, target, k, min);

		return res;
	}

	/**
	 * Compute bounded reachability/until probabilities.
	 * i.e. compute the min/max probability of reaching a state in {@code target},
	 * within time t, and while remaining in states in @{code remain}.
	 * @param ctmdp The CTMDP
	 * @param remain Remain in these states (optional: null means "all")
	 * @param target Target states
	 * @param t: Time bound
	 * @param min Min or max probabilities (true=min, false=max)
	 * @param init: Initial solution vector - pass null for default
	 * @param results: Optional array of size b+1 to store (init state) results for each step (null if unused)
	 */
	public ModelCheckerResult computeBoundedReachProbsOld(CTMDP ctmdp, BitSet remain, BitSet target, double t, boolean min, double init[], double results[]) throws PrismException
	{
		// TODO: implement until
		
		ModelCheckerResult res = null;
		int i, n, iters;
		double soln[], soln2[], tmpsoln[], sum[];
		long timer;
		// Fox-Glynn stuff
		FoxGlynn fg;
		int left, right;
		double q, qt, weights[], totalWeight;

		// Start bounded probabilistic reachability
		timer = System.currentTimeMillis();
		mainLog.println("Starting time-bounded probabilistic reachability...");

		// Store num states
		n = ctmdp.getNumStates();

		// Get uniformisation rate; do Fox-Glynn
		q = 99;//ctmdp.unif;
		qt = q * t;
		mainLog.println("\nUniformisation: q.t = " + q + " x " + t + " = " + qt);
		fg = new FoxGlynn(qt, 1e-300, 1e+300, termCritParam / 8.0);
		left = fg.getLeftTruncationPoint();
		right = fg.getRightTruncationPoint();
		if (right < 0) {
			throw new PrismException("Overflow in Fox-Glynn computation (time bound too big?)");
		}
		weights = fg.getWeights();
		totalWeight = fg.getTotalWeight();
		for (i = left; i <= right; i++) {
			weights[i - left] /= totalWeight;
		}
		mainLog.println("Fox-Glynn: left = " + left + ", right = " + right);

		// Create solution vector(s)
		soln = new double[n];
		soln2 = (init == null) ? new double[n] : init;
		sum = new double[n];

		// Initialise solution vectors. Use passed in initial vector, if present
		if (init != null) {
			for (i = 0; i < n; i++)
				soln[i] = soln2[i] = target.get(i) ? 1.0 : init[i];
		} else {
			for (i = 0; i < n; i++)
				soln[i] = soln2[i] = target.get(i) ? 1.0 : 0.0;
		}
		for (i = 0; i < n; i++)
			sum[i] = 0.0;

		// If necessary, do 0th element of summation (doesn't require any matrix powers)
		if (left == 0)
			for (i = 0; i < n; i++)
				sum[i] += weights[0] * soln[i];

		// Start iterations
		iters = 1;
		while (iters <= right) {
			// Matrix-vector multiply and min/max ops
			ctmdp.mvMultMinMax(soln, min, soln2, target, true, null);
			// Since is globally uniform, can do this? and more?
			for (i = 0; i < n; i++)
				soln2[i] /= q;
			// Store intermediate results if required
			// TODO?
			// Swap vectors for next iter
			tmpsoln = soln;
			soln = soln2;
			soln2 = tmpsoln;
			// Add to sum
			if (iters >= left) {
				for (i = 0; i < n; i++)
					sum[i] += weights[iters - left] * soln[i];
			}
			iters++;
		}

		// Print vector (for debugging)
		mainLog.println(sum);

		// Finished bounded probabilistic reachability
		timer = System.currentTimeMillis() - timer;
		mainLog.print("Time-bounded probabilistic reachability (" + (min ? "min" : "max") + ")");
		mainLog.println(" took " + iters + " iters and " + timer / 1000.0 + " seconds.");

		// Return results
		res = new ModelCheckerResult();
		res.soln = sum;
		res.lastSoln = soln2;
		res.numIters = iters;
		res.timeTaken = timer / 1000.0;
		return res;
	}

	/**
	 * Simple test program.
	 */
	public static void main(String args[])
	{
		CTMDPModelChecker mc;
		CTMDPSimple ctmdp;
		ModelCheckerResult res;
		BitSet target;
		Map<String, BitSet> labels;
		boolean min = true;
		try {
			mc = new CTMDPModelChecker();
			ctmdp = new CTMDPSimple();
			ctmdp.buildFromPrismExplicit(args[0]);
			System.out.println(ctmdp);
			labels = mc.loadLabelsFile(args[1]);
			System.out.println(labels);
			target = labels.get(args[2]);
			if (target == null)
				throw new PrismException("Unknown label \"" + args[2] + "\"");
			for (int i = 4; i < args.length; i++) {
				if (args[i].equals("-min"))
					min = true;
				else if (args[i].equals("-max"))
					min = false;
				else if (args[i].equals("-nopre"))
					mc.setPrecomp(false);
			}
			res = mc.computeBoundedReachProbs(ctmdp, null, target, Double.parseDouble(args[3]), min, null, null);
			System.out.println(res.soln[0]);
		} catch (PrismException e) {
			System.out.println(e);
		}
	}
}
