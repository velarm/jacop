/**
 *  NonTransitiveDice.java
 *  This file is part of JaCoP.
 *
 *  JaCoP is a Java Constraint Programming solver.
 *
 *	Copyright (C) 2000-2008 Krzysztof Kuchcinski and Radoslaw Szymanek
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  Notwithstanding any other provision of this License, the copyright
 *  owners of this work supplement the terms of this License with terms
 *  prohibiting misrepresentation of the origin of this work and requiring
 *  that modified versions of this work be marked in reasonable ways as
 *  different from the original version. This supplement of the license
 *  terms is in accordance with Section 7 of GNU Affero General Public
 *  License version 3.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.jacop.satwrapper.examples;

import java.util.ArrayList;

import org.jacop.constraints.Alldifferent;
import org.jacop.constraints.Constraint;
import org.jacop.constraints.Max;
import org.jacop.constraints.Min;
import org.jacop.constraints.Reified;
import org.jacop.constraints.Sum;
import org.jacop.constraints.XeqC;
import org.jacop.constraints.XgtY;
import org.jacop.constraints.XltY;
import org.jacop.constraints.XplusYeqC;
import org.jacop.core.IntVar;
import org.jacop.core.Store;
import org.jacop.core.Var;
import org.jacop.examples.fd.ExampleFD;
import org.jacop.satwrapper.SatWrapper;
import org.jacop.search.DepthFirstSearch;
import org.jacop.search.IndomainMiddle;
import org.jacop.search.SelectChoicePoint;
import org.jacop.search.SimpleSelect;

/**
 *
 * It models and solves Nontransitive Dice Problem.
 *
 * @author Radoslaw Szymanek
 *
 * Non transitive Dice problem is to assign to given number of dices
 * a number to each side of the dice in such a way that
 *
 * a) given cyclic order of dices, each dice wins with the next one
 * with probability p larger than 0.5.
 *
 * b) maximize minimum p.
 *
 * c) no two dices which are matched against each other can result
 *    in draw. default approach to satisfy this condition is to
 *    require all sides of all dices to be assigned unique values.
 *
 */
@SuppressWarnings("all")
public class SemiSatNonTransitiveDice extends ExampleFD {

/*
     * 
run:
3	6	17	true	133	71	62	53	18	
3	6	16	true	2647	1327	1320	1310	26	
3	6	15	true	16040	8022	8018	8006	36	
3	6	14	false	4167	2083	2084	2083	27	
3	6	18	false	0	0	0	0	0	
3	6	17	true	117	63	54	45	18	
3	6	16	true	1955	981	974	964	24	
3	6	15	true	6814	3409	3405	3391	25	
3	6	14	false	1405	702	703	702	20	
BUILD SUCCESSFUL (total time: 2 seconds)
    
3	6	17	true	89	49	40	31	18	
3	6	16	true	1690	848	842	835	26	
3	6	15	true	6337	3170	3167	3158	28	
3	6	14	false	1981	990	991	990	24	
3	6	18	false	0	0	0	0	0	
3	6	17	true	75	42	33	24	18	
3	6	16	true	1188	597	591	584	25	
3	6	15	true	2019	1011	1008	993	27	
3	6	14	false	841	420	421	420	20	
BUILD SUCCESSFUL (total time: 1 second)
*/
    
	/**
	 * It specifies number of dices in the problem.
	 */
	public int noDices = 3;

	/**
	 * It specifies number of sides for each dice in the problem.
	 */
	public int noSides = 4;


	/**
	 * It specifies the currently best solution which is a bound
	 * for the next solution.
	 *
	 * The currentBest specifies the difference between noSides^2
	 * and minimumWinning. Since we maximize minimumWinning we
	 * minimize currentBest. The next solution must have a lower
	 * value for currentBest. currentBest is an upperbound.
	 *
	 * minimumWinning + currentBest = noSides^2
	 *
	 * Good initial value for currentBest is noSides^2 / 2.
	 */

	public int currentBest = 16;

	/**
	 * It contains constraints which can be used for shaving guidance.
	 */
	public ArrayList<Constraint> shavingConstraints = new ArrayList<Constraint>();

	/**
	 * If true then faces on non consequtive faces can be the same.
	 */
	public boolean reuseOfNumbers = false;

	// the wrapper used to interface with the SAT solver
	public SatWrapper wrapper;
	
	@Override
	public void model() {

		store = new Store();
		int noNumbers = noDices * noSides;

                // create a SAT solver wrapper
		wrapper = new SatWrapper();
		store.impose(wrapper);
                
		IntVar faces[] = new IntVar[noDices * noSides];

		for (int i = 0; i < faces.length; i++) {
			// Create FDV for each face
			faces[i] = new IntVar(store, "d" + (i / noSides + 1) + "f"	+ (i % noSides + 1),
					// minimal value
					1,
					// maximal value
					noNumbers);
			wrapper.register(faces[i]);
		}

		// Faces are lexigraphically ordered
		for (int i = 0; i < noDices; i++)
			for (int j = 0; j < noSides - 1; j++)
				// Impose constraints that each consequtive face
				// is smaller than the previous one
				store.impose(new XltY(faces[i * noSides + j], faces[i * noSides + j + 1]));

		IntVar wins[][][] = new IntVar[noDices][noSides][noSides];

		for (int i = 0; i < noDices; i++)
			for (int j = 0; j < noSides; j++)
				for (int m = 0; m < noSides; m++)
					wins[i][j][m] = new IntVar(store, "win_D" + (i + 1) + "->"
							+ ((i + 2) % noDices) + "F" + j + m, 0, 1);

		// Winning constraints if Fj from ith dice is larger than Fm from
		// ith+1 dice than wins[i][j][m] is equal to 1.
		for (int i = 0; i < noDices; i++)
			for (int j = 0; j < noSides; j++)
				for (int m = 0; m < noSides; m++)
					store.impose(new Reified(new XgtY(
							faces[noSides * i + j], faces[noSides
							                              * ((i + 1) % noDices) + m]),
							                              wins[i][j][m]));

		// Special implied constraints (type 1)
		// do not decrease number of backtracks
		// for (int i = 0; i < noDices; i ++)
		// for (int j = 0; j < noSides; j++)
		// for (int m = 0; m < noSides - 1; m++)
		// store.impose(new XgteqY(wins[i][j][m], wins[i][j][m+1]));

		// for (int i = 0; i < noDices; i ++)
		// for (int m = 0; m < noSides; m++)
		// for (int j = noSides - 1; j > 0; j--)
		// store.impose(new XgteqY(wins[i][j][m], wins[i][j - 1][m]));

		// Another type of implied constraints, they do reduce no of
		// backtracks.
		// If the winning probability is given as parameter to the program
		// then use it.
		for (int j = 0; j < noSides; j++)
			for (int m = 0; m < noSides; m++)
				if (currentBest != noSides * noSides) {
					if ((j + 1) * (noSides - m) > currentBest - 1)
						for (int i = 0; i < noDices; i++)
							store.impose(new XeqC(wins[i][j][m], 1));
				} else if ((j + 1) * (noSides - m) > ((noSides * noSides) / 2))
					for (int i = 0; i < noDices; i++)
						store.impose(new XeqC(wins[i][j][m], 1));

		IntVar winningSum[] = new IntVar[noDices];
		for (int i = 0; i < noDices; i++) {
			winningSum[i] = new IntVar(store, "noWins-d" + (i + 1) + "->d"
					+ ((i + 2) % noDices), noSides * noSides / 2 + 1,
					noSides * noSides);

			IntVar matrix[] = new IntVar[noSides * noSides];
			for (int j = 0; j < noSides; j++)
				for (int m = 0; m < noSides; m++)
					matrix[j * noSides + m] = wins[i][j][m];

			store.impose(new Sum(matrix, winningSum[i]));
		}

		IntVar minimumWinning = new IntVar(store, "MinDominance", 0, noSides * noSides);

		store.impose(new Min(winningSum, minimumWinning));

		// FDV noSidesSquare = new FDV(store, "noSidesSquare",
		// noSides*noSides, noSides*noSides);

		// minimumWinning + diff = noSidesSquare
		// Objective to maximize minimumWinning is equal to minimization
		// objective
		// of diff
		IntVar diff = new IntVar(store, "diff", currentBest, currentBest);

		store.impose(new XplusYeqC(minimumWinning, diff, noSides * noSides));

		// create SAT wrapper
		wrapper = new SatWrapper();
		store.impose(wrapper);

		if (reuseOfNumbers) {

			// Why not only restriction that all faces of any two
			// consecutive dices
			// should be different?

			@SuppressWarnings("unchecked")
			IntVar[] sides_two_consecutive_dices = new IntVar[noSides * 2];

			for (int i = 0; i < noDices; i++) {

				for (int j = 0; j < noSides; j++) {
					sides_two_consecutive_dices[j] = faces[noSides * i + j];
					sides_two_consecutive_dices[j + noSides] = faces[noSides
					                                                 * ((i + 1) % noDices) + j];
				}
				Alldifferent cx = new Alldifferent(sides_two_consecutive_dices);

				// store.impose(cx);
				cx.imposeToSat(wrapper);

			}
		} else {

			Alldifferent cx = new Alldifferent(faces);

			// store.impose(cx, 1);
			cx.imposeToSat(wrapper);

		}

		// Symmetry breaking between dices
		store.impose(new XeqC(faces[0], 1));

		// Minimizing maximal number on dice

		IntVar maxNo = new IntVar(store, "maxNo", noSides * 2, noDices * noSides);
		store.impose(new Max(faces, maxNo));

		// Simple maximum constraint on cost variable
		// store.setLevel(store.level + 1);

		vars = new ArrayList<IntVar>();

		for (int i = noSides / 2, j = noSides / 2 + 1; i >= 0
		|| j < noSides; i--, j++) {
			for (int d = 0; d < noDices; d++)
				if (i >= 0)
					vars.add(faces[d * noSides + i]);
			for (int d = 0; d < noDices; d++)
				if (j < noSides)
					vars.add(faces[d * noSides + j]);
		}
		
	}

	/**
	 * It executes a specialized search to find a solution to this problem. It uses
	 * input order, indomain middle, and limit of backtracks. It prints
	 * major search statistics.
	 *
	 * @return true if solution is found, false otherwise.
	 */
	public boolean searchSpecial() {

		search = new DepthFirstSearch<IntVar>();
		search.setPrintInfo(false);
		search.setBacktracksOut(10000000);

		SelectChoicePoint select = new SimpleSelect(vars.toArray(new Var[1]), null,
				new IndomainMiddle());

		boolean result = search.labeling(store, select);

		System.out.print(noDices + "\t");
		System.out.print(noSides + "\t");
		System.out.print(currentBest + "\t");
		System.out.print(result + "\t");
		System.out.print(search.getNodes() + "\t");
		System.out.print(search.getDecisions() + "\t");
		System.out.print(search.getWrongDecisions() + "\t");
		System.out.print(search.getBacktracks() + "\t");
		System.out.println(search.getMaximumDepth() + "\t");

		return result;

	}


	/**
	 * It executes the program solving non transitive dice problem using
	 * two different methods. The second method employs constraint guided shaving.
	 * @param args the first argument specifies number of dices, the second argument specifies the number of sides of each dice.
	 */
	public static void main(String args[]) {

/*            
3	6	17	true	89	49	40	31	18	
3	6	16	true	1690	848	842	835	26	
3	6	15	true	6321	3162	3159	3150	28	
3	6	14	false	2065	1032	1033	1032	24	
3	6	18	false	0	0	0	0	0	
3	6	17	true	75	42	33	24	18	
3	6	16	true	1196	601	595	588	25	
3	6	15	true	2033	1018	1015	1000	27	
3	6	14	false	917	458	459	458	19	
BUILD SUCCESSFUL (total time: 1 second)
*/

		int sols = 0;

		boolean firstSolutionFound = false;

		int noDices = 3;
		if (args.length > 0)
			noDices = new Integer(args[0]);

		int noSides = 6;
		if (args.length > 1)
			noSides = new Integer(args[1]);

		int currentBest;

		if (noSides * noSides % 2 == 0)
			currentBest = noSides * noSides / 2 - 1;
		else
			currentBest = noSides * noSides / 2;

		while (true) {

			SemiSatNonTransitiveDice example = new SemiSatNonTransitiveDice();

			example.noDices = noDices;
			example.noSides = noSides;
			example.currentBest = currentBest;

			example.model();

			boolean result = example.searchSpecial();
			
			currentBest--;

			if (result) {
				firstSolutionFound = true;
				sols++;
			}

			if (!result && firstSolutionFound)
				break;

		}

		firstSolutionFound = false;
		currentBest = noSides * noSides / 2;

		while (true) {

			SemiSatNonTransitiveDice example = new SemiSatNonTransitiveDice();

			example.noDices = noDices;
			example.noSides = noSides;
			example.currentBest = currentBest;

			example.model();
			
			boolean result = example.shavingSearch(example.shavingConstraints, false);
			
			
			System.out.print(noDices + "\t");
			System.out.print(noSides + "\t");
			System.out.print(currentBest + "\t");
			System.out.print(result + "\t");
			System.out.print(example.search.getNodes() + "\t");
			System.out.print(example.search.getDecisions() + "\t");
			System.out.print(example.search.getWrongDecisions() + "\t");
			System.out.print(example.search.getBacktracks() + "\t");
			System.out.println(example.search.getMaximumDepth() + "\t");

			currentBest--;

			if (result) {
				firstSolutionFound = true;
				sols++;
			}

			if (!result && firstSolutionFound)
				break;

		}

	}



}

