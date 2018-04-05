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
		setPriority(OverlayPriority.LOW);
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

		Color color = Color.WHITE;
		String layout = plugin.getRaid().getLayout().toCode();
		int position;

		if (plugin.getCurrentRoom() != null)
		{
			position = plugin.getCurrentRoom().getIndex();

			if (position > 1)
			{
				layout = "<col=" + Integer.toHexString(Color.GREEN.getRGB() & 0xFFFFFF) + ">" +
						layout.substring(0, position) + "<col=ffffff>" +
						layout.substring(position, layout.length());
			}
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

		boolean isWhitelistedRotation = false;

		if (config.enableRotationWhitelist())
		{
			isWhitelistedRotation = plugin.isRotationWhitelisted();
		}

		for (int i = 0; i < plugin.getRaid().getLayout().getRooms().size(); i++)
		{
			RaidRoom room = plugin.getRaid().getRoomAt(i);

			if (room == null)
			{
				continue;
			}

			color = Color.WHITE;

			switch (room.getType())
			{
				case COMBAT:
					if (room.isCompleted() || !plugin.isRaidOngoing() &&
							(plugin.getRoomWhitelist().contains(room.getBoss().getName().toLowerCase()) ||
									config.enableRotationWhitelist() && isWhitelistedRotation))
					{
						color = Color.GREEN;
					}
					else if (!plugin.isRaidOngoing() && (plugin.getRoomBlacklist().contains(room.getBoss().getName().toLowerCase())
							|| config.enableRotationWhitelist()))
					{
						color = Color.RED;
					}

					panelComponent.getLines().add(new PanelComponent.Line(
						room.getType().getName(), Color.WHITE, room.getBoss().getName(), color
					));
					break;

				case PUZZLE:
					if (room.isCompleted() || !plugin.isRaidOngoing() && plugin.getRoomWhitelist().contains(room.getPuzzle().getName().toLowerCase()))
					{
						color = Color.GREEN;
					}
					else if (!plugin.isRaidOngoing() && (plugin.getRoomBlacklist().contains(room.getPuzzle().getName().toLowerCase())))
					{
						color = Color.RED;
					}

					panelComponent.getLines().add(new PanelComponent.Line(
						room.getType().getName(), Color.WHITE, room.getPuzzle().getName(), color
					));
					break;

				case START:
					String str = room.getIndex() == 0 ? "First floor" : "Second floor";
					panelComponent.getLines().add(new PanelComponent.Line(
							str, Color.WHITE, "", Color.WHITE
					));
					break;
			}
		}

		return panelComponent.render(graphics);
	}
}
