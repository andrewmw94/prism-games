// ==============================================================================
//	
// Copyright (c) 2002-
// Authors:
// * Dave Parker <david.parker@comlab.ox.ac.uk> (University of Oxford)
//	
// ------------------------------------------------------------------------------
//	
// This file is part of PRISM.
//	
// PRISM is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//	
// PRISM is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//	
// You should have received a copy of the GNU General Public License
// along with PRISM; if not, write to the Free Software Foundation,
// Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
//	
// ==============================================================================

package explicit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import explicit.rewards.STPGRewards;

import prism.ModelType;
import prism.PrismException;

/**
 * Simple explicit-state representation of a stochastic two-player game
 * (STPG). States can be labelled arbitrarily with player 1 player 2.
 */
public class STPGExplicit extends MDPSimple implements STPG
{
	// state labels
	public static final int PLAYER_1 = 1;
	public static final int PLAYER_2 = 2;

	protected List<Integer> stateLabels;

	public STPGExplicit()
	{
		super();

		// initialising state labels
		stateLabels = new ArrayList<Integer>(numStates);
	}

	/**
	 * Construct an STPG from an existing one and a state index permutation,
	 * i.e. in which state index i becomes index permut[i].
	 */
	public STPGExplicit(STPGExplicit stpg, int permut[])
	{
		super(stpg, permut);
		stateLabels = new ArrayList<Integer>(numStates);
		// Create blank array of correct size
		for (int i = 0; i < numStates; i++) {
			stateLabels.add(0);
		}
		// Copy permuted player info
		for (int i = 0; i < numStates; i++) {
			stateLabels.set(permut[i], stpg.stateLabels.get(i));
		}
	}

	@Override
	public void prob0step(BitSet subset, BitSet u, boolean forall1, boolean forall2, BitSet result)
	{
		int i;
		boolean b1, b2;
		boolean forall = false;

		for (i = 0; i < numStates; i++) {
			if (subset.get(i)) {

				if (stateLabels.get(i) == PLAYER_1)
					forall = forall1;
				else if (stateLabels.get(i) == PLAYER_2)
					forall = forall2;

				b1 = forall; // there exists or for all
				for (Distribution distr : trans.get(i)) {
					b2 = distr.containsOneOf(u);
					if (forall) {
						if (!b2) {
							b1 = false;
							continue;
						}
					} else {
						if (b2) {
							b1 = true;
							continue;
						}
					}
				}
				result.set(i, b1);
			}
		}
	}

	@Override
	public void prob1step(BitSet subset, BitSet u, BitSet v, boolean forall1, boolean forall2, BitSet result)
	{
		int i;
		boolean b1, b2;
		boolean forall = false;

		for (i = 0; i < numStates; i++) {
			if (subset.get(i)) {

				if (stateLabels.get(i) == PLAYER_1)
					forall = forall1;
				else if (stateLabels.get(i) == PLAYER_2)
					forall = forall2;

				b1 = forall; // there exists or for all
				for (Distribution distr : trans.get(i)) {
					b2 = distr.containsOneOf(v) && distr.isSubsetOf(u);
					if (forall) {
						if (!b2) {
							b1 = false;
							continue;
						}
					} else {
						if (b2) {
							b1 = true;
							continue;
						}
					}
				}
				result.set(i, b1);
			}
		}
	}

	// TODO fix the method
	@Override
	public void mvMultMinMax(double vect[], boolean min1, boolean min2, double result[], BitSet subset, boolean complement, int adv[])
	{
		int s;
		boolean min = false;
		// Loop depends on subset/complement arguments
		if (subset == null) {
			for (s = 0; s < numStates; s++) {
				if (stateLabels.get(s) == PLAYER_1)
					min = min1;
				else if (stateLabels.get(s) == PLAYER_2)
					min = min2;

				result[s] = mvMultMinMaxSingle(s, vect, min, adv);
			}
		} else if (complement) {
			for (s = subset.nextClearBit(0); s < numStates; s = subset.nextClearBit(s + 1)) {
				if (stateLabels.get(s) == PLAYER_1)
					min = min1;
				else if (stateLabels.get(s) == PLAYER_2)
					min = min2;

				result[s] = mvMultMinMaxSingle(s, vect, min, adv);
			}
		} else {
			for (s = subset.nextSetBit(0); s >= 0; s = subset.nextSetBit(s + 1)) {
				if (stateLabels.get(s) == PLAYER_1)
					min = min1;
				else if (stateLabels.get(s) == PLAYER_2)
					min = min2;
				result[s] = mvMultMinMaxSingle(s, vect, min, adv);
			}
		}
	}

	@Override
	public double mvMultMinMaxSingle(int s, double vect[], boolean min1, boolean min2)
	{
		boolean min = stateLabels.get(s) == PLAYER_1 ? min1 : stateLabels.get(s) == PLAYER_2 ? min2 : false;
		return mvMultMinMaxSingle(s, vect, min, null);
	}

	@Override
	public List<Integer> mvMultMinMaxSingleChoices(int s, double vect[], boolean min1, boolean min2, double val)
	{
		boolean min = stateLabels.get(s) == PLAYER_1 ? min1 : stateLabels.get(s) == PLAYER_2 ? min2 : false;
		return mvMultMinMaxSingleChoices(s, vect, min, val);
	}

	@Override
	public double mvMultGSMinMax(double vect[], boolean min1, boolean min2, BitSet subset, boolean complement, boolean absolute)
	{
		int s;
		double d, diff, maxDiff = 0.0;
		// Loop depends on subset/complement arguments
		if (subset == null) {
			for (s = 0; s < numStates; s++) {
				d = mvMultJacMinMaxSingle(s, vect, min1, min2);
				diff = absolute ? (Math.abs(d - vect[s])) : (Math.abs(d - vect[s]) / d);
				maxDiff = diff > maxDiff ? diff : maxDiff;
				vect[s] = d;
			}
		} else if (complement) {
			for (s = subset.nextClearBit(0); s < numStates; s = subset.nextClearBit(s + 1)) {
				d = mvMultJacMinMaxSingle(s, vect, min1, min2);
				diff = absolute ? (Math.abs(d - vect[s])) : (Math.abs(d - vect[s]) / d);
				maxDiff = diff > maxDiff ? diff : maxDiff;
				vect[s] = d;
			}
		} else {
			for (s = subset.nextSetBit(0); s >= 0; s = subset.nextSetBit(s + 1)) {
				d = mvMultJacMinMaxSingle(s, vect, min1, min2);
				diff = absolute ? (Math.abs(d - vect[s])) : (Math.abs(d - vect[s]) / d);
				maxDiff = diff > maxDiff ? diff : maxDiff;
				vect[s] = d;
			}
		}
		return maxDiff;
	}

	@Override
	public double mvMultJacMinMaxSingle(int s, double vect[], boolean min1, boolean min2)
	{
		boolean min = stateLabels.get(s) == PLAYER_1 ? min1 : stateLabels.get(s) == PLAYER_2 ? min2 : false;
		return mvMultJacMinMaxSingle(s, vect, min);
	}

	@Override
	public void mvMultRewMinMax(double vect[], STPGRewards rewards, boolean min1, boolean min2, double result[], BitSet subset, boolean complement, int adv[])
	{
		int s;
		boolean min = false;
		// Loop depends on subset/complement arguments
		if (subset == null) {
			for (s = 0; s < numStates; s++) {
				if (stateLabels.get(s) == PLAYER_1)
					min = min1;
				else if (stateLabels.get(s) == PLAYER_2)
					min = min2;
				// TODO: convert/pass rewards
				result[s] = mvMultRewMinMaxSingle(s, vect, null, min, adv);
			}
		} else if (complement) {
			for (s = subset.nextClearBit(0); s < numStates; s = subset.nextClearBit(s + 1)) {
				if (stateLabels.get(s) == PLAYER_1)
					min = min1;
				else if (stateLabels.get(s) == PLAYER_2)
					min = min2;
				// TODO: convert/pass rewards
				result[s] = mvMultRewMinMaxSingle(s, vect, null, min, adv);
			}
		} else {
			for (s = subset.nextSetBit(0); s >= 0; s = subset.nextSetBit(s + 1)) {
				if (stateLabels.get(s) == PLAYER_1)
					min = min1;
				else if (stateLabels.get(s) == PLAYER_2)
					min = min2;
				// TODO: convert/pass rewards
				result[s] = mvMultRewMinMaxSingle(s, vect, null, min, adv);
			}
		}
	}

	@Override
	public double mvMultRewMinMaxSingle(int s, double vect[], STPGRewards rewards, boolean min1, boolean min2, int adv[])
	{
		boolean min = stateLabels.get(s) == PLAYER_1 ? min1 : stateLabels.get(s) == PLAYER_2 ? min2 : false;
		// TODO: convert/pass rewards
		return mvMultRewMinMaxSingle(s, vect, null, min, null);
	}

	@Override
	public List<Integer> mvMultRewMinMaxSingleChoices(int s, double vect[], STPGRewards rewards, boolean min1, boolean min2, double val)
	{
		boolean min = stateLabels.get(s) == PLAYER_1 ? min1 : stateLabels.get(s) == PLAYER_2 ? min2 : false;
		// TODO: convert/pass rewards
		return mvMultRewMinMaxSingleChoices(s, vect, null, min, val);
	}

	/**
	 * Adds one state, assigned to player 1
	 */
	@Override
	public int addState()
	{
		return addState(PLAYER_1);
	}

	/**
	 * Adds specified number of states all assigned to player 1
	 */
	@Override
	public void addStates(int numToAdd)
	{
		super.addStates(numToAdd);
		for (int i = 0; i < numToAdd; i++)
			stateLabels.add(PLAYER_1);
	}

	/**
	 * Adds state assigned to the specified player
	 * 
	 * @param player state owner
	 * @return state id
	 */
	public int addState(int player)
	{
		checkPlayer(player);

		super.addStates(1);
		stateLabels.add(player);

		//System.out.println("State " + (numStates - 1) + " player " + player);

		return numStates - 1;
	}

	/**
	 * Adds the number of states the same as number of Integer in the list, each
	 * assigned to the corresponding player
	 * 
	 * @param players list of players (to which corresponding state belongs)
	 */
	public void addStates(List<Integer> players)
	{
		checkPlayers(players);

		super.addStates(players.size());
		stateLabels.addAll(players);
	}

	/**
	 * labels the given state with the given player
	 * 
	 * @param s state
	 * @param player player
	 */
	public void setPlayer(int s, int player)
	{
		checkPlayer(player);
		if (s < stateLabels.size())
			stateLabels.set(s, player);
	}

	/** Checks whether the given player is valid and throws exception otherwise **/
	private void checkPlayer(int player)
	{
		switch (player) {
		case PLAYER_1:
			return;
		case PLAYER_2:
			return;
		}
		throw new IllegalArgumentException("Player " + player + " is undefined!");
	}

	/**
	 * Checks whether every player in the list is valid and throws exception
	 * otherwise
	 **/
	private void checkPlayers(List<Integer> players)
	{
		for (Integer p : players)
			checkPlayer(p);
	}

	@Override
	public ModelType getModelType()
	{
		return ModelType.STPG;
	}

	/**
	 * Get transition function as string.
	 */
	public String toString()
	{
		int i, j, n;
		Object o;
		String s = "";
		s = "[ ";
		for (i = 0; i < numStates; i++) {
			if (i > 0)
				s += ", ";
			s += i + "(P-" + stateLabels.get(i) + "): ";
			s += "[";
			n = getNumChoices(i);
			for (j = 0; j < n; j++) {
				if (j > 0)
					s += ",";
				o = getAction(i, j);
				if (o != null)
					s += o + ":";
				s += trans.get(i).get(j);
			}
			s += "]";
		}
		s += " ]\n";
		return s;
	}

	public static void main(String[] args)
	{
		STPGModelChecker mc;
		STPGExplicit stpg;
		DistributionSet set;
		Distribution distr;
		ModelCheckerResult res;
		BitSet target;

		// Simple example: Create and solve the stochastic game from:
		// Mark Kattenbelt, Marta Kwiatkowska, Gethin Norman, David Parker
		// A Game-based Abstraction-Refinement Framework for Markov Decision
		// Processes
		// Formal Methods in System Design 36(3): 246-280, 2010

		try {
			// Build game
			stpg = new STPGExplicit();

			/*
			// state 0
			stpg.addState(PLAYER_1);
			set = stpg.newDistributionSet(null);
			distr = new Distribution();
			distr.set(1, 1.0);
			set.add(distr);
			stpg.addDistributionSet(0, set);

			// state 1
			stpg.addState(PLAYER_2);
			set = stpg.newDistributionSet(null);
			distr = new Distribution();
			distr.set(2, 1.0);
			set.add(distr);
			stpg.addDistributionSet(1, set);

			// state 2
			stpg.addState(PLAYER_1);
			set = stpg.newDistributionSet(null);
			distr = new Distribution();
			distr.set(3, 1.0);
			set.add(distr);
			distr = new Distribution();
			distr.set(4, 1.0);
			set.add(distr);
			stpg.addDistributionSet(2, set);

			// state 3
			stpg.addState(PLAYER_2);
			set = stpg.newDistributionSet(null);
			distr = new Distribution();
			distr.set(5, 1.0);
			set.add(distr);
			distr = new Distribution();
			distr.set(2, 1.0);
			set.add(distr);
			stpg.addDistributionSet(3, set);

			// state 4
			stpg.addState(PLAYER_2);
			set = stpg.newDistributionSet(null);
			distr = new Distribution();
			distr.set(6, 1.0);
			set.add(distr);
			distr = new Distribution();
			distr.set(5, 0.5);
			distr.set(6, 0.5);
			set.add(distr);
			stpg.addDistributionSet(4, set);

			// state 5
			stpg.addState(PLAYER_1);
			set = stpg.newDistributionSet(null);
			distr = new Distribution();
			distr.set(7, 1.0);
			set.add(distr);
			stpg.addDistributionSet(5, set);

			// state 6
			stpg.addState(PLAYER_1);
			set = stpg.newDistributionSet(null);
			distr = new Distribution();
			distr.set(8, 1.0);
			set.add(distr);
			stpg.addDistributionSet(6, set);

			// state 7
			stpg.addState(PLAYER_2);
			set = stpg.newDistributionSet(null);
			distr = new Distribution();
			distr.set(7, 1.0);
			set.add(distr);
			stpg.addDistributionSet(7, set);

			// state 8
			stpg.addState(PLAYER_2);
			set = stpg.newDistributionSet(null);
			distr = new Distribution();
			distr.set(8, 1.0);
			set.add(distr);
			stpg.addDistributionSet(8, set);
			*/

			// Print game
			System.out.println(stpg);

			// Model check
			mc = new STPGModelChecker();
			// mc.setVerbosity(2);
			target = new BitSet();
			target.set(8);
			stpg.exportToDotFile("stpg.dot", target);
			System.out.println("min min: " + mc.computeReachProbs(stpg, target, true, true).soln[0]);
			System.out.println("max min: " + mc.computeReachProbs(stpg, target, false, true).soln[0]);
			System.out.println("min max: " + mc.computeReachProbs(stpg, target, true, false).soln[0]);
			System.out.println("max max: " + mc.computeReachProbs(stpg, target, false, false).soln[0]);
		} catch (PrismException e) {
			System.out.println(e);
		}
	}

}
