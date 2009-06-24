package org.pokenet.server.battle.impl;

import org.pokenet.server.backend.entity.PlayerChar;
import org.pokenet.server.battle.BattleField;
import org.pokenet.server.battle.BattleTurn;
import org.pokenet.server.battle.Pokemon;
import org.pokenet.server.battle.mechanics.BattleMechanics;
import org.pokenet.server.battle.mechanics.MoveQueueException;
import org.pokenet.server.battle.mechanics.statuses.StatusEffect;
import org.pokenet.server.battle.mechanics.statuses.field.FieldEffect;
import org.pokenet.server.battle.mechanics.statuses.field.HailEffect;
import org.pokenet.server.battle.mechanics.statuses.field.RainEffect;
import org.pokenet.server.battle.mechanics.statuses.field.SandstormEffect;
import org.pokenet.server.feature.TimeService;
import org.pokenet.server.network.ProtocolHandler;
import org.pokenet.server.network.message.battle.BattleEndMessage;
import org.pokenet.server.network.message.battle.BattleInitMessage;
import org.pokenet.server.network.message.battle.BattleMessage;
import org.pokenet.server.network.message.battle.BattleMoveMessage;
import org.pokenet.server.network.message.battle.BattleMoveRequest;
import org.pokenet.server.network.message.battle.EnemyDataMessage;
import org.pokenet.server.network.message.battle.FaintMessage;
import org.pokenet.server.network.message.battle.HealthChangeMessage;
import org.pokenet.server.network.message.battle.NoPPMessage;
import org.pokenet.server.network.message.battle.StatusChangeMessage;
import org.pokenet.server.network.message.battle.SwitchMessage;
import org.pokenet.server.network.message.battle.SwitchRequest;
import org.pokenet.server.network.message.battle.BattleEndMessage.BattleEnd;

/**
 * A class which handles PvP battles
 * 
 * @author shadowkanji
 * 
 */
public class PvPBattleField extends BattleField {
	private PlayerChar[] m_players;
	private BattleTurn[] m_turn = new BattleTurn[2];

	/**
	 * Constructor
	 * 
	 * @param mech
	 * @param p1
	 * @param p2
	 */
	public PvPBattleField(BattleMechanics mech, PlayerChar p1, PlayerChar p2) {
		super(mech, new Pokemon[][] { p1.getParty(), p2.getParty() });
		/*
		 * Store the players
		 */
		m_players = new PlayerChar[2];
		m_players[0] = p1;
		m_players[1] = p2;
		/*
		 * Set the battlefield for the players
		 */
		p1.setBattleField(this);
		p2.setBattleField(this);
		/*
		 *Set the player to battling 
		 */
		p1.setBattling(true);
		p2.setBattling(true);
		/*
		 * Set battle ids
		 */
		p1.setBattleId(0);
		p2.setBattleId(1);

		/*
		 * Send battle initialisation packets
		 */
		ProtocolHandler.writeMessage(p1.getSession(), 
				new BattleInitMessage(false, p2.getPartyCount()));
		ProtocolHandler.writeMessage(p2.getSession(), 
				new BattleInitMessage(false, p1.getPartyCount()));
		/* Send the enemy's name to both players*/
		p1.getSession().write("bn" + p2.getName());
		p2.getSession().write("bn" + p1.getName());
		/* Send pokemon data to both players */
		sendPokemonData(p1, p2);
		sendPokemonData(p2, p1);
		/* Apply weather and request moves */
		//applyWeather();
		requestMoves();
	}

	/**
	 * Sends pokemon data for PlayerChar p to receiver
	 * 
	 * @param p
	 * @param receiver
	 */
	private void sendPokemonData(PlayerChar p, PlayerChar receiver) {
		for (int i = 0; i < p.getParty().length; i++) {
			if (p.getParty()[i] != null) {
				ProtocolHandler.writeMessage(receiver.getSession(), 
						new EnemyDataMessage(i, p.getParty()[i]));
			}
		}
	}

	@Override
	public void applyWeather() {
		if (m_players[0].getMap().isWeatherForced()) {
			switch (m_players[0].getMap().getWeather()) {
			case NORMAL:
				return;
			case RAIN:
				this.applyEffect(new RainEffect());
				return;
			case HAIL:
				this.applyEffect(new HailEffect());
				return;
			case SANDSTORM:
				this.applyEffect(new SandstormEffect());
				return;
			default:
				return;
			}
		} else {
			FieldEffect f = TimeService.getWeatherEffect();
			if (f != null) {
				this.applyEffect(f);
			}
		}
	}

	@Override
	public void clearQueue() {
		m_turn[0] = null;
		m_turn[1] = null;
	}

	@Override
	public BattleTurn[] getQueuedTurns() {
		return m_turn;
	}

	@Override
	public String getTrainerName(int idx) {
		return m_players[idx].getName();
	}

	@Override
	public void informPokemonFainted(int trainer, int idx) {
		ProtocolHandler.writeMessage(m_players[0].getSession(), 
				new FaintMessage(getParty(trainer)[idx].getSpeciesName()));
		ProtocolHandler.writeMessage(m_players[1].getSession(), 
				new FaintMessage(getParty(trainer)[idx].getSpeciesName()));
	}

	@Override
	public void informPokemonHealthChanged(Pokemon poke, int change) {
		if (poke != null) {
			if (poke == m_players[0].getParty()[0]) {
				ProtocolHandler.writeMessage(m_players[0].getSession(), 
						new HealthChangeMessage(0 , change));
				ProtocolHandler.writeMessage(m_players[1].getSession(), 
						new HealthChangeMessage(1 , change));
			} else {
				ProtocolHandler.writeMessage(m_players[1].getSession(), 
						new HealthChangeMessage(0 , change));
				ProtocolHandler.writeMessage(m_players[0].getSession(), 
						new HealthChangeMessage(1 , change));
			}
		}
	}

	@Override
	public void informStatusApplied(Pokemon poke, StatusEffect eff) {
		if (poke != null) {
			if (poke == m_players[0].getParty()[0]) {
				ProtocolHandler.writeMessage(m_players[0].getSession(), 
						new StatusChangeMessage(0, 
								poke.getSpeciesName(), 
								eff.getName(), false));
				ProtocolHandler.writeMessage(m_players[1].getSession(), 
						new StatusChangeMessage(1, 
								poke.getSpeciesName(), 
								eff.getName(), false));
			} else {
				ProtocolHandler.writeMessage(m_players[0].getSession(), 
						new StatusChangeMessage(1, 
								poke.getSpeciesName(), 
								eff.getName(), false));
				ProtocolHandler.writeMessage(m_players[1].getSession(), 
						new StatusChangeMessage(0, 
								poke.getSpeciesName(), 
								eff.getName(), false));
			}
		}
	}

	@Override
	public void informStatusRemoved(Pokemon poke, StatusEffect eff) {
		if (poke != null) {
			if (poke == m_players[0].getParty()[0]) {
				ProtocolHandler.writeMessage(m_players[0].getSession(), 
						new StatusChangeMessage(0, 
								poke.getSpeciesName(), 
								eff.getName(), true));
				ProtocolHandler.writeMessage(m_players[1].getSession(), 
						new StatusChangeMessage(1, 
								poke.getSpeciesName(), 
								eff.getName(), true));
			} else {
				ProtocolHandler.writeMessage(m_players[0].getSession(), 
						new StatusChangeMessage(1, 
								poke.getSpeciesName(), 
								eff.getName(), true));
				ProtocolHandler.writeMessage(m_players[1].getSession(), 
						new StatusChangeMessage(0, 
								poke.getSpeciesName(), 
								eff.getName(), true));
			}
		}
	}

	@Override
	public void informSwitchInPokemon(int trainer, Pokemon poke) {
		if (trainer == 0) {
			ProtocolHandler.writeMessage(m_players[0].getSession(), 
					new SwitchMessage(m_players[0].getName(),
							poke.getSpeciesName(),
							trainer,
							getPokemonPartyIndex(poke)));
			ProtocolHandler.writeMessage(m_players[1].getSession(), 
					new SwitchMessage(m_players[0].getName(),
							poke.getSpeciesName(),
							trainer,
							getPokemonPartyIndex(poke)));
		} else {
			ProtocolHandler.writeMessage(m_players[0].getSession(), 
					new SwitchMessage(m_players[1].getName(),
							poke.getSpeciesName(),
							trainer,
							getPokemonPartyIndex(poke)));
			ProtocolHandler.writeMessage(m_players[1].getSession(), 
					new SwitchMessage(m_players[1].getName(),
							poke.getSpeciesName(),
							trainer,
							getPokemonPartyIndex(poke)));
		}
	}

	@Override
	public void informUseMove(Pokemon poke, String name) {
		ProtocolHandler.writeMessage(m_players[0].getSession(), 
				new BattleMoveMessage(poke.getSpeciesName(), name));
		ProtocolHandler.writeMessage(m_players[1].getSession(), 
				new BattleMoveMessage(poke.getSpeciesName(), name));
	}

	@SuppressWarnings("deprecation")
	@Override
	public void informVictory(int winner) {
		m_players[0].removeTempStatusEffects();
		m_players[1].removeTempStatusEffects();
		if (winner == 0) {
			ProtocolHandler.writeMessage(m_players[0].getSession(), 
					new BattleEndMessage(BattleEnd.WON));
			ProtocolHandler.writeMessage(m_players[1].getSession(), 
					new BattleEndMessage(BattleEnd.LOST));
			m_players[1].lostBattle();
		} else {
			ProtocolHandler.writeMessage(m_players[0].getSession(), 
					new BattleEndMessage(BattleEnd.LOST));
			ProtocolHandler.writeMessage(m_players[1].getSession(), 
					new BattleEndMessage(BattleEnd.WON));
			m_players[0].lostBattle();
		}
		m_players[0].setBattling(false);
		m_players[1].setBattling(false);
		if (m_dispatch != null) {
			/*
			 * This very bad programming but shoddy does it and forces us to do
			 * it
			 */
			Thread t = m_dispatch;
			m_dispatch = null;
			t.stop();
		}
		dispose();
	}

	@Override
	public void queueMove(int trainer, BattleTurn move)
			throws MoveQueueException {
		/* Handle forced switches */
		if(m_isWaiting && m_replace != null && m_replace[trainer]) {
			if(!move.isMoveTurn()) {
				if(getActivePokemon()[trainer].compareTo(this.getParty(trainer)[move.getId()]) != 0) {
					this.switchInPokemon(trainer, move.getId());
					m_replace[trainer] = false;
					m_isWaiting = false;
					return;
				}
			}
			requestPokemonReplacement(trainer);
			return;
		}
		// The trainer has no turn queued.
		if (m_turn[trainer] == null) {
			/* Handle Pokemon being unhappy and ignoring you */
			if(!getActivePokemon()[trainer].isFainted()) {
				if(getActivePokemon()[trainer].getHappiness() <= 40) {
					/* Pokemon is unhappy, they'll do what they feel like */
					showMessage(getActivePokemon()[trainer].getSpeciesName() + " is unhappy!");
					int moveID = getMechanics().getRandom().nextInt(4);
					while (getActivePokemon()[trainer].getMove(moveID) == null)
						moveID = getMechanics().getRandom().nextInt(4);
					move = BattleTurn.getMoveTurn(moveID);
				} else if(getActivePokemon()[trainer].getHappiness() < 70) {
					/* Pokemon is partially unhappy, 50% chance they'll listen to you */
					if(getMechanics().getRandom().nextInt(2) == 1) {
						showMessage(getActivePokemon()[trainer].getSpeciesName() + " is unhappy!");
						int moveID = getMechanics().getRandom().nextInt(4);
						while (getActivePokemon()[trainer].getMove(moveID) == null)
							moveID = getMechanics().getRandom().nextInt(4);
						move = BattleTurn.getMoveTurn(moveID);
					}
				}
			}
			if (move.getId() == -1) {
				if (m_dispatch == null
						&& ((trainer == 0 && m_turn[1] != null) ||
								(trainer == 1 && m_turn[0] != null))) {
					m_dispatch = new Thread(new Runnable() {
						public void run() {
							executeTurn(m_turn);
							m_dispatch = null;
						}
					});
					m_dispatch.start();
					return;
				}
			} else {
				// Handle a fainted pokemon
				if (this.getActivePokemon()[trainer].isFainted()) {
					if (!move.isMoveTurn() && this.getParty(trainer)[move.getId()] != null
							&& this.getParty(trainer)[move.getId()].getHealth() > 0) {
						switchInPokemon(trainer, move.getId());
						requestMoves();
						return;
					} else {
						// The player still has pokemon left
						if (getAliveCount(trainer) > 0) {
							requestPokemonReplacement(trainer);
							return;
						} else {
							// the player has no pokemon left. Announce winner
							if (trainer == 0)
								this.informVictory(1);
							else
								this.informVictory(0);
							return;
						}
					}
				} else {
					// The turn was used to attack!
					if (move.isMoveTurn()) {
						// Handles Struggle
						if (getActivePokemon()[trainer].mustStruggle())
							m_turn[trainer] = BattleTurn.getMoveTurn(-1);
						else {
							// The move has no more PP. Tell the client!
							if (this.getActivePokemon()[trainer].getPp(move
									.getId()) <= 0) {
								if (trainer == 0) {
									ProtocolHandler.writeMessage(m_players[0].getSession(), 
											new NoPPMessage(this.getActivePokemon()[trainer]
												.getMoveName(move.getId())));
								} else {
									ProtocolHandler.writeMessage(m_players[1].getSession(), 
											new NoPPMessage(this.getActivePokemon()[trainer]
												.getMoveName(move.getId())));
								}
								return;
							} else {
								// Assign the move to the turn
								m_turn[trainer] = move;
							}
						}
					} else {
						if (this.getActivePokemon()[trainer].isActive() && 
								this.getParty(trainer)[move.getId()] != null &&
								this.getParty(trainer)[move.getId()].getHealth() > 0) {
							m_turn[trainer] = move;
						} else {
							requestMove(trainer);
							return;
						}
					}
				}
			}
		}
		if (m_dispatch != null)
			return;
		// Both turns are ready to be performed 
		if (m_turn[0] != null && m_turn[1] != null) {
			m_dispatch = new Thread(new Runnable() {
				public void run() {
					executeTurn(m_turn);
					for (int i = 0; i < m_participants; ++i) {
						m_turn[i] = null;
					}
					m_dispatch = null;
				}
			});
			m_dispatch.start();
		}
	}

	@Override
	public void refreshActivePokemon() {
		m_players[0].getSession().write(
				"bh0" + this.getActivePokemon()[0].getHealth());
		m_players[0].getSession().write(
				"bh1" + this.getActivePokemon()[1].getHealth());

		m_players[1].getSession().write(
				"bh0" + this.getActivePokemon()[1].getHealth());
		m_players[1].getSession().write(
				"bh1" + this.getActivePokemon()[0].getHealth());
	}

	@Override
	public void requestAndWaitForSwitch(int party) {
		requestPokemonReplacement(party);
		if (!m_replace[party]) {
			return;
		}
		m_isWaiting = true;
		do {
			synchronized (m_dispatch) {
				try {
					m_dispatch.wait(1000);
				} catch (InterruptedException e) {
				}
			}
		} while ((m_replace != null) && m_replace[party]);
	}

	@Override
	protected void requestMove(int trainer) {
		ProtocolHandler.writeMessage(m_players[trainer].getSession(), 
				new BattleMoveRequest());
	}

	@Override
	protected void requestMoves() {
		clearQueue();
		if (this.getActivePokemon()[0].isActive()
				&& this.getActivePokemon()[1].isActive()) {
			ProtocolHandler.writeMessage(m_players[0].getSession(), 
					new BattleMoveRequest());
			ProtocolHandler.writeMessage(m_players[1].getSession(), 
					new BattleMoveRequest());
		}
	}

	@Override
	protected void requestPokemonReplacement(int i) {
		ProtocolHandler.writeMessage(m_players[i].getSession(), 
				new SwitchRequest());
	}

	@Override
	public void showMessage(String message) {
		if(m_players != null) {
			if(m_players[0] != null)
				ProtocolHandler.writeMessage(m_players[0].getSession(), 
					new BattleMessage(message));
			if(m_players[1] != null)
				ProtocolHandler.writeMessage(m_players[1].getSession(), 
					new BattleMessage(message));
		}
	}

}