/*
 * Copyright (c) 2018, Kamiel
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.raids;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.ProgressPie;

public class RaidsCrabsOverlay extends Overlay
{
	private static final Color CRAB_STUN_TIMER_COLOR = Color.ORANGE;

	@Inject
	private Client client;

	private RaidsPlugin plugin;
	private RaidsConfig config;

	@Inject
	public RaidsCrabsOverlay(RaidsPlugin plugin, RaidsConfig config)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(OverlayPriority.LOW);
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isRaidOngoing() || !config.crabStunTimer())
		{
			return null;
		}

		RaidRoom currentRoom = plugin.getCurrentRoom();
		if (currentRoom != null)
		{
			if (config.crabStunTimer() && currentRoom.getType() == RaidRoom.Type.PUZZLE && currentRoom.getPuzzle() == RaidRoom.Puzzle.CRABS)
			{
				for (Map.Entry<NPC, Instant> entry : plugin.getStunnedCrabs().entrySet())
				{
					NPC npc = entry.getKey();
					Duration timeLeft = Duration.between(Instant.now(), entry.getValue());
					int seconds = (int) (timeLeft.toMillis() / 1000L);

					if (seconds < 1)
					{
						plugin.getCurrentRoomNPCs().remove(npc);
					}
					else
					{
						drawStunTimer(graphics, npc, seconds);
					}
				}
			}
		}

		return null;
	}

	private void drawStunTimer(Graphics2D graphics, NPC npc, int seconds)
	{
		double progress = (double) seconds / RaidsPlugin.CRAB_STUN_LENGTH;
		Point location = npc.getCanvasTextLocation(graphics, "", npc.getLogicalHeight() / 2 - 25 / 2);
		ProgressPie pie = new ProgressPie();
		pie.setBorderColor(CRAB_STUN_TIMER_COLOR);
		pie.setFill(CRAB_STUN_TIMER_COLOR);
		pie.render(graphics, location, progress);
/*
		//Construct the arc
		Arc2D.Float arc = new Arc2D.Float(Arc2D.PIE);
		arc.setAngleStart(90);
		arc.setAngleExtent(timeLeft * 360);
		arc.setFrame(loc.getX() - 25 / 2, loc.getY() - 25 / 2, 25, 25);

		//Draw the inside of the arc
		graphics.setColor(Color.ORANGE);
		graphics.fill(arc);

		//Draw the outlines of the arc
		graphics.setColor(Color.ORANGE);
		graphics.setStroke(new BasicStroke(1));
		graphics.drawOval(loc.getX() - 25 / 2, loc.getY() - 25 / 2, 25, 25);*/
	}
}
