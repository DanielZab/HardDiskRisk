# Agent Description
This repository contains our implementation of a risk agent for the course "[188.981 Programmierung von Strategie-Spielen9(https://tiss.tuwien.ac.at/course/educationDetails.xhtml?dswid=7736&dsrid=837&semester=2022W&courseNr=188981)" at the TU-Wien. We were already provided with the Risk framework, therefore this repository only contains our additions.

## General Idea
Generally, the agent has different heuristics for the different
subphases. Herefore we made use of MCTS, move pruning and setting priorities for nodes. Additionally, the agent makes decisions within the MCTS on which move to pick next with the help of a neural network trained in python.

## Selection Phase
In the beginning phase of every game, which we called “selection phase”,
HardDiskRisk will first alternate his picks between South America and Australia
as long as possible.
Once these two continents have no free territories left, the agent will check,
in which continent the enemy hast the highest ratio of countries and choose
his next pick in the same region.
If all territories are occupied, the agent will reinforce South America and
Australia first. After a certain number of troops, the remaining reinforcements
will again be spread according to the enemy’s occupation ratio.

## Reinforcement Phase
In the reinforcement phase the move tree is pruned, so that the agent only
reinforces territories, which are on or near the border to the enemy. If a territory is more
than two steps away from an enemy territory, it will be cut out.

## Fortify Phase
In the fortify phase all countries have a priority for fortification, so that countries that
are not near a boarder get a higher priority in moving the troops to a different location. The more troops a country not near a border has, the higher the priority to move the troops away.

## Game State Evaluation
We implemented a function, that evaluates how favourable the current game state is
for our agent. It evaluates each state using the amount and distribution of the troops
of the players, the number of territories occupied, the number of complete continents
in possession and the number of cards. The result of this method is always between
1 (very good situation) and 0 (very bad situation).

## Neural Network
For our neural network we needed to encode the current
state of the game as well as the next move and pass it to our neural network, which tries to
predict how favourable the given move will turn out for our agent within 2 rounds.
This number must not be too high, since we did not want too many moves to influence
their effects. In our opinion, however, choosing 1 round would be too low since the
effect of an action cannot unfold properly within this short period. Therefore, we
decided upon predicting the outcome in 2 rounds. Please note that due to the regulations we
had to convert the tensorflow model to an onnx model in order for it to work in Java

## Encoding the game state
We encoded the current state of the game for the neural network as follows:
First for each territory the number of troops is written. If the number is negative,
they belong to the enemy, otherwise they are coded as troops of our agent. This
is followed by the number of cards we own and how many are left. Next is the
game phase, which is one hot encoded. Finally, the next action is encoded.  
