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
import javax.inject.Inject;
import lombok.Setter;
import net.runelite.client.plugins.raids.solver.Room;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.PanelComponent;

public class RaidsScoutOverlay extends Overlay
{
	private RaidsPlugin plugin;
	private RaidsConfig config;
	private final PanelComponent panelComponent = new PanelComponent();

	@Setter
	private boolean scoutOverlayShown = false;

	@Inject
	public RaidsScoutOverlay(RaidsPlugin plugin, RaidsConfig config)
	{
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(OverlayPriority.MED);
		this.plugin = plugin;
		this.config = config;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.scoutOverlay() || !scoutOverlayShown)
		{
			return null;
		}

		panelComponent.getLines().clear();

		if (plugin.getRaid() == null || plugin.getRaid().getLayout() == null)
		{
			panelComponent.setTitleColor(Color.RED);
			panelComponent.setTitle("Unable to scout this raid!");
			return panelComponent.render(graphics);
		}

		panelComponent.setTitleColor(Color.WHITE);
		panelComponent.setTitle("Raid scouter");

		boolean raidOngoing = plugin.isRaidOngoing();
		Color color = Color.WHITE;
		String layout = plugin.getRaid().getLayout().toCode();

		if (raidOngoing)
		{
			int progress = plugin.getRaid().getProgress();

			layout = "<col=" + Integer.toHexString(Color.GREEN.getRGB() & 0xFFFFFF) + ">" +
				layout.substring(0, progress) + "<col=" + Integer.toHexString(color.getRGB() & 0xFFFFFF) + ">" +
				layout.substring(progress, layout.length());
		}

		layout = layout.replaceAll("#", "").replaceAll("¤", "");

		if (config.enableLayoutWhitelist())
		{
			if (plugin.getLayoutWhitelist().contains(layout.toLowerCase()))
			{
				color = Color.GREEN;
			}
			else
			{
				color = Color.RED;
			}
		}

		panelComponent.getLines().add(new PanelComponent.Line(
				"Layout", Color.WHITE, layout, color
		));

		boolean isWhitelistedRotation = config.enableRotationWhitelist() && plugin.isRotationWhitelisted();

		for (Room layoutRoom : plugin.getRaid().getLayout().getRooms())
		{
			int position = layoutRoom.getPosition();
			RaidRoom room = plugin.getRaid().getRoom(position);

			if (room == null)
			{
				continue;
			}

			color = raidOngoing ? (room.isCompleted() ? Color.GREEN : Color.WHITE) : Color.WHITE;

			switch (room.getType())
			{
				case COMBAT:
					if (!raidOngoing)
					{
						if (plugin.getRoomWhitelist().contains(room.getBoss().getName().toLowerCase())
							|| config.enableRotationWhitelist() && isWhitelistedRotation)
						{
							color = Color.GREEN;
						}
						else if (plugin.getRoomBlacklist().contains(room.getBoss().getName().toLowerCase()) || config.enableRotationWhitelist())
						{
							color = Color.RED;
						}
					}

					panelComponent.getLines().add(new PanelComponent.Line(
						room.getType().getName(), Color.WHITE, room.getBoss().getName(), color
					));
					break;

				case PUZZLE:
					if (!raidOngoing)
					{
						if (plugin.getRoomWhitelist().contains(room.getPuzzle().getName().toLowerCase()))
						{
							color = Color.GREEN;
						}
						else if (plugin.getRoomBlacklist().contains(room.getPuzzle().getName().toLowerCase()))
						{
							color = Color.RED;
						}
					}

					panelComponent.getLines().add(new PanelComponent.Line(
						room.getType().getName(), Color.WHITE, room.getPuzzle().getName(), color
					));
					break;
			}
		}

		return panelComponent.render(graphics);
	}
}
