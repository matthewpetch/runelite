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

import com.google.common.eventbus.Subscribe;
import com.google.inject.Binder;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GraphicID;
import net.runelite.api.InstanceTemplates;
import net.runelite.api.NPC;
import net.runelite.api.ObjectID;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.VarPlayer;
import net.runelite.api.Tile;
import net.runelite.api.Varbits;
import static net.runelite.api.Perspective.SCENE_SIZE;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.MapRegionChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetHiddenChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.chat.ChatColor;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.raids.solver.Layout;
import net.runelite.client.plugins.raids.solver.LayoutSolver;
import net.runelite.client.plugins.raids.solver.RotationSolver;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Chambers Of Xeric"
)
@Slf4j
public class RaidsPlugin extends Plugin
{
	private static final int LOBBY_PLANE = 3;
	private static final String RAID_START_MESSAGE = "The raid has begun!";
	private static final String LEVEL_COMPLETE_MESSAGE = "level complete!";
	private static final String RAID_COMPLETE_MESSAGE = "Congratulations - your raid is complete!";
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("###.##");
	public static final DecimalFormat POINTS_FORMAT = new DecimalFormat("#,###");
	private static final String SPLIT_REGEX = "\\s*,\\s*";
	private static final Pattern ROTATION_REGEX = Pattern.compile("\\[(.*?)\\]");
	public static final int CRAB_STUN_LENGTH = 25;
	private static final Duration CRAB_STUN_DURATION = Duration.ofSeconds(CRAB_STUN_LENGTH);

	private BufferedImage raidsIcon;
	private RaidsTimer timer;
	private boolean checkUnknownRooms = false;

	@Getter
	private RaidRoom currentRoom;

	@Getter
	private boolean inRaidChambers;

	@Getter
	private boolean raidOngoing;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private Client client;

	@Inject
	private RaidsConfig config;

	@Inject
	private RaidsScoutOverlay scoutOverlay;

	@Inject
	private RaidsPointsOverlay pointsOverlay;

	@Inject
	private RaidsCrabsOverlay crabsOverlay;

	@Inject
	private RaidsVanguardsOverlay vanguardsOverlay;

	@Inject
	private LayoutSolver layoutSolver;

	@Getter
	private Raid raid;

	@Getter
	private List<String> roomWhitelist = new ArrayList<>();

	@Getter
	private List<String> roomBlacklist = new ArrayList<>();

	@Getter
	private List<String> rotationWhitelist = new ArrayList<>();

	@Getter
	private List<String> layoutWhitelist = new ArrayList<>();

	@Getter
	private List<NPC> currentRoomNPCs = new ArrayList<>();

	@Getter
	private Map<NPC, Instant> stunnedCrabs = new HashMap<>();

	@Provides
	RaidsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RaidsConfig.class);
	}

	@Override
	public void configure(Binder binder)
	{
		binder.bind(RaidsScoutOverlay.class);
	}

	@Override
	public List<Overlay> getOverlays()
	{
		return Arrays.asList(scoutOverlay, pointsOverlay, crabsOverlay, vanguardsOverlay);
	}

	@Override
	protected void startUp() throws Exception
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			inRaidChambers = client.getVar(Varbits.IN_RAID) == 1;
			updateInfoBoxState();
		}

		if (config.pointsMessage())
		{
			cacheColors();
		}

		updateLists();
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (timer != null)
		{
			infoBoxManager.removeInfoBox(timer);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (config.pointsMessage())
		{
			cacheColors();
		}

		if (event.getKey().equals("raidsTimer"))
		{
			updateInfoBoxState();
		}

		if (event.getKey().equals("scoutOverlayAtBank"))
		{
			if (raid != null && !inRaidChambers)
			{
				scoutOverlay.setScoutOverlayShown(Boolean.valueOf(event.getNewValue()));
			}
		}

		if (event.getKey().equals("scoutOverlayDuringRaid"))
		{
			if (raid != null && raidOngoing)
			{
				scoutOverlay.setScoutOverlayShown(Boolean.valueOf(event.getNewValue()));
			}
		}

		if (event.getKey().equals("whitelistedRooms"))
		{
			updateList(roomWhitelist, config.whitelistedRooms());
		}

		if (event.getKey().equals("blacklistedRooms"))
		{
			updateList(roomBlacklist, config.blacklistedRooms());
		}

		if (event.getKey().equals("whitelistedRotations"))
		{
			updateList(rotationWhitelist, config.whitelistedRotations());
		}

		if (event.getKey().equals("whitelistedLayouts"))
		{
			updateList(layoutWhitelist, config.whitelistedLayouts());
		}
	}

	@Subscribe
	public void onWidgetHiddenChanged(WidgetHiddenChanged event)
	{
		if (!inRaidChambers || event.isHidden())
		{
			return;
		}

		Widget widget = event.getWidget();

		if (widget == client.getWidget(WidgetInfo.RAIDS_POINTS_INFOBOX))
		{
			widget.setHidden(true);
		}
	}

	@Subscribe
	public void onVarbitChange(VarbitChanged event)
	{
		boolean setting = client.getVar(Varbits.IN_RAID) == 1;

		if (inRaidChambers != setting)
		{
			inRaidChambers = setting;
			updateInfoBoxState();

			if (inRaidChambers)
			{
				prepareScoutOverlay();
			}
			else if (!config.scoutOverlayAtBank())
			{
				resetOverlays();
			}
		}

		if (client.getSetting(Setting.IN_RAID_PARTY) == -1)
		{
			if (inRaidChambers)
			{
				raidOngoing = true;

				if (!config.scoutOverlayDuringRaid())
				{
					scoutOverlay.setScoutOverlayShown(false);
				}
				else if (raid == null)
				{
					prepareScoutOverlay();
				}
			}
			else
			{
				raidOngoing = false;
				resetOverlays();
			}
		}
	}

	private void resetOverlays()
	{
		scoutOverlay.setScoutOverlayShown(false);
		raid = null;
		currentRoom = null;
		currentRoomNPCs.clear();
	}

	private void prepareScoutOverlay()
	{
		raid = buildRaid();

		if (raid == null)
		{
			log.debug("Failed to build raid");
			return;
		}

<<<<<<< HEAD
		Layout layout = layoutSolver.findLayout(raid.toCode());

		if (layout == null)
=======
		if (client.getVar(VarPlayer.IN_RAID_PARTY) == -1)
>>>>>>> pr/6
		{
			log.debug("Could not find layout match");
			return;
		}

		raid.updateLayout(layout);
		RotationSolver.solve(raid.getCombatRooms());
		currentRoom = raid.getRoomAt(0);
		scoutOverlay.setScoutOverlayShown(true);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (inRaidChambers && event.getType() == ChatMessageType.CLANCHAT_INFO)
		{
			String message = Text.removeTags(event.getMessage());

			if (config.raidsTimer() && message.startsWith(RAID_START_MESSAGE))
			{
				timer = new RaidsTimer(getRaidsIcon(), this, Instant.now());
				infoBoxManager.addInfoBox(timer);
			}

			if (timer != null && message.contains(LEVEL_COMPLETE_MESSAGE))
			{
				timer.timeFloor();
			}

			if (message.startsWith(RAID_COMPLETE_MESSAGE))
			{
				if (timer != null)
				{
					timer.timeFloor();
					timer.setStopped(true);
				}

				if (config.pointsMessage())
				{
					int totalPoints = client.getVar(Varbits.TOTAL_POINTS);
					int personalPoints = client.getVar(Varbits.PERSONAL_POINTS);

					double percentage = personalPoints / (totalPoints / 100.0);

					String chatMessage = new ChatMessageBuilder()
							.append(ChatColorType.NORMAL)
							.append("Total points: ")
							.append(ChatColorType.HIGHLIGHT)
							.append(POINTS_FORMAT.format(totalPoints))
							.append(ChatColorType.NORMAL)
							.append(", Personal points: ")
							.append(ChatColorType.HIGHLIGHT)
							.append(POINTS_FORMAT.format(personalPoints))
							.append(ChatColorType.NORMAL)
							.append(" (")
							.append(ChatColorType.HIGHLIGHT)
							.append(DECIMAL_FORMAT.format(percentage))
							.append(ChatColorType.NORMAL)
							.append("%)")
							.build();

					chatMessageManager.queue(QueuedMessage.builder()
						.type(ChatMessageType.CLANCHAT_INFO)
						.runeLiteFormattedMessage(chatMessage)
						.build());
				}
			}
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		if (!raidOngoing || currentRoom == null)
		{
			return;
		}

		RaidRoom.Type currentType = currentRoom.getType();
		if (currentType == RaidRoom.Type.PUZZLE)
		{
			if (currentRoom.getPuzzle() == RaidRoom.Puzzle.CRABS)
			{
				Actor actor = event.getActor();
				if (actor instanceof NPC && currentRoom.contains(actor.getWorldLocation()))
				{
					if (actor.getGraphic() == GraphicID.STUN_STARS)
					{
						stunnedCrabs.put((NPC) actor, Instant.now().plus(CRAB_STUN_DURATION));
					}
				}
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!raidOngoing || raid == null || client.getPlane() < 2)
		{
			return;
		}

		loadCurrentNPCs();

		if (checkUnknownRooms && client.getGameState() != GameState.LOADING)
		{
			checkUnknownRooms();
			checkUnknownRooms = false;
		}

		if (config.vanguardsOverlay() && currentRoom.getBoss() == RaidRoom.Boss.VANGUARDS)
		{
			vanguardsOverlay.updateVanguardsHealth();
		}

		final Player player = client.getLocalPlayer();
		int position = currentRoom.getIndex();

		RaidRoom next = raid.getRoomAt(position + 1);

		if (next == null)
		{
			return;
		}

		if (next.contains(player.getWorldLocation()))
		{
			currentRoom.setCompleted(true);
			currentRoom = next;
		}
		else if (!currentRoom.contains(player.getWorldLocation()))
		{
			currentRoom = raid.getRoomAt(position - 1);
		}
	}

	@Subscribe
	public void mapRegionChanged(MapRegionChanged event)
	{
		if (raidOngoing && config.scoutOverlayDuringRaid())
		{
			checkUnknownRooms = true;
		}
	}

	public void repositionPointsBox()
	{
		Widget widget = client.getWidget(WidgetInfo.RAIDS_POINTS_INFOBOX);
		int x = widget.getParent().getWidth() - widget.getWidth() - 2;
		int y = widget.getOriginalY();

		if (client.getSetting(Varbits.EXPERIENCE_TRACKER_POSITION) == 0)
		{
			Widget area = client.getWidget(WidgetInfo.EXPERIENCE_TRACKER_BOTTOM_BAR);

			if (area != null)
			{
				y = area.getOriginalY() + 2;
				area.setRelativeY(y + widget.getHeight());
			}
		}

		widget.setRelativeX(x);
		widget.setRelativeY(y);
	}

	private void loadCurrentNPCs()
	{
		currentRoomNPCs.clear();

		if (currentRoom == null)
		{
			return;
		}

		for (NPC npc : client.getNpcs())
		{
			if (currentRoom.contains(npc.getWorldLocation()))
			{
				currentRoomNPCs.add(npc);
			}
		}
	}

	private void updateInfoBoxState()
	{
		if (timer != null)
		{
			if (inRaidChambers && config.raidsTimer())
			{
				infoBoxManager.addInfoBox(timer);
			}
			else
			{
				infoBoxManager.removeInfoBox(timer);
			}

			if (!inRaidChambers)
			{
				timer = null;
			}
		}
	}

	private void updateLists()
	{
		updateList(roomWhitelist, config.whitelistedRooms());
		updateList(roomBlacklist, config.blacklistedRooms());
		updateList(rotationWhitelist, config.whitelistedRotations());
		updateList(layoutWhitelist, config.whitelistedLayouts());
	}

	private void updateList(List<String> list, String input)
	{
		list.clear();

		if (list == rotationWhitelist)
		{
			Matcher m = ROTATION_REGEX.matcher(input);
			while (m.find())
			{
				String rotation = m.group(1).toLowerCase().replaceAll(" ", "");

				if (!list.contains(rotation))
				{
					list.add(rotation);
				}
			}
		}
		else
		{
			list.addAll(Arrays.asList(input.toLowerCase().split(SPLIT_REGEX)));
		}
	}

	private void cacheColors()
	{
		chatMessageManager.cacheColor(new ChatColor(ChatColorType.NORMAL, Color.BLACK, false), ChatMessageType.CLANCHAT_INFO)
				.cacheColor(new ChatColor(ChatColorType.HIGHLIGHT, Color.RED, false), ChatMessageType.CLANCHAT_INFO)
				.cacheColor(new ChatColor(ChatColorType.NORMAL, Color.WHITE, true), ChatMessageType.CLANCHAT_INFO)
				.cacheColor(new ChatColor(ChatColorType.HIGHLIGHT, Color.RED, true), ChatMessageType.CLANCHAT_INFO)
				.refreshAll();
	}

	public boolean isRotationWhitelisted()
	{
		String rotation = raid.getRotationString().toLowerCase();

		if (rotationWhitelist.contains(rotation))
		{
			return true;
		}

		String[] bosses = rotation.split(SPLIT_REGEX);
		for (String whitelisted : rotationWhitelist)
		{
			String[] whitelistedBosses = whitelisted.split(SPLIT_REGEX);

			if (bosses.length >= whitelistedBosses.length)
			{
				continue;
			}

			boolean matches = false;

			for (int i = 0; i < bosses.length; i++)
			{
				if (!whitelistedBosses[i].equals(bosses[i]))
				{
					matches = false;
					break;
				}

				matches = true;
			}

			if (matches)
			{
				return true;
			}
		}


		return false;
	}

	private Point findLobbyBase()
	{
		Tile[][] tiles = client.getRegion().getTiles()[LOBBY_PLANE];

		for (int x = 0; x < SCENE_SIZE; x++)
		{
			for (int y = 0; y < SCENE_SIZE; y++)
			{
				if (tiles[x][y] == null || tiles[x][y].getWallObject() == null)
				{
					continue;
				}

				if (tiles[x][y].getWallObject().getId() == ObjectID.NULL_12231)
				{
					return tiles[x][y].getRegionLocation();
				}
			}
		}

		return null;
	}

	private Raid buildRaid()
	{
		Point gridBase = findLobbyBase();

		if (gridBase == null)
		{
			return null;
		}

		Raid raid = new Raid();
		Tile[][] tiles;
		int position, x, y, offsetX;
		int startY = 1;
		int startX = -2;
		int baseX = 0;

		for (int plane = 3; plane > 1; plane--)
		{
			tiles = client.getRegion().getTiles()[plane];

			if (tiles[gridBase.getX() + RaidRoom.ROOM_MAX_SIZE][gridBase.getY()] == null)
			{
				position = 1;
				baseX = -RaidRoom.ROOM_MAX_SIZE;
			}
			else
			{
				position = 0;
			}

			for (int i = startY; i > -2; i--)
			{
				y = gridBase.getY() + (i * RaidRoom.ROOM_MAX_SIZE);

				for (int j = startX; j < 4; j++)
				{
					x = gridBase.getX() + (j * RaidRoom.ROOM_MAX_SIZE);
					offsetX = 0;

					if (x > SCENE_SIZE && position > 1 && position < 4)
					{
						position++;
					}

					if (x < 0)
					{
						offsetX = Math.abs(x) + 1; //add 1 because the tile at x=0 will always be null
					}

					if (x < SCENE_SIZE && y >= 0 && y < SCENE_SIZE)
					{
						if (tiles[x + offsetX][y] == null)
						{
							if (position == 4)
							{
								position++;
								break;
							}

							continue;
						}

						if (position == 0)
						{
							startX = j;
							startY = i;
						}

						Tile base = tiles[offsetX > 0 ? 1 : x][y];
						RaidRoom room = determineRoom(base, plane, offsetX);
						raid.setRoom(room, position + Math.abs((plane - 3) * 8));
						position++;
					}
				}
			}
		}

		baseX += gridBase.getX() + RaidRoom.ROOM_MAX_SIZE * startX;
		int baseY = gridBase.getY() - RaidRoom.ROOM_MAX_SIZE + (RaidRoom.ROOM_MAX_SIZE * startY);
		raid.setBase(WorldPoint.fromRegion(client, baseX, baseY, 0));
		return raid;
	}

	private void checkUnknownRooms()
	{
		for (RaidRoom room : raid.getRooms())
		{
			if (room == null)
			{
				continue;
			}

			if (room.getType() == RaidRoom.Type.UNKNOWN || room.getType() == RaidRoom.Type.COMBAT && room.getBoss() == RaidRoom.Boss.UNKNOWN ||
					room.getType() == RaidRoom.Type.PUZZLE && room.getPuzzle() == RaidRoom.Puzzle.UNKNOWN)
			{
				int x = room.getBase().getX() - client.getBaseX();
				int y = room.getBase().getY() - client.getBaseY();

				if (x <= -32 || x >= SCENE_SIZE || y >= SCENE_SIZE)
				{
					continue;
				}

				int plane = room.getBase().getPlane();
				Tile tile = client.getRegion().getTiles()[plane][x <= 0 ? 1 : x][y];

				if (tile == null)
				{
					System.out.println("Tile null?!");
					continue;
				}

				RaidRoom raidRoom = determineRoom(tile, plane, 0);
				room.setBoss(raidRoom.getBoss());
				room.setPuzzle(raidRoom.getPuzzle());
			}
		}

		RotationSolver.solve(raid.getCombatRooms());
	}

	private RaidRoom determineRoom(Tile tile, int plane, int offset)
	{
		WorldPoint base = new WorldPoint(tile.getWorldLocation().getX() - offset, tile.getWorldLocation().getY(), plane);
		RaidRoom room = new RaidRoom(base, RaidRoom.Type.EMPTY);
		int chunkData = client.getInstanceTemplateChunks()[tile.getPlane()][(tile.getRegionLocation().getX()) / 8][tile.getRegionLocation().getY() / 8];
		InstanceTemplates template = InstanceTemplates.findMatch(chunkData);

		if (template == null)
		{
			return room;
		}

		switch (template)
		{
			case RAIDS_LOBBY:
			case RAIDS_START:
				room.setType(RaidRoom.Type.START);
				break;

			case RAIDS_END:
				room.setType(RaidRoom.Type.END);
				break;

			case RAIDS_SCAVENGERS:
			case RAIDS_SCAVENGERS2:
				room.setType(RaidRoom.Type.SCAVENGERS);
				break;

			case RAIDS_SHAMANS:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.SHAMANS);
				break;

			case RAIDS_VASA:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.VASA);
				break;

			case RAIDS_VANGUARDS:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.VANGUARDS);
				break;

			case RAIDS_ICE_DEMON:
				room.setType(RaidRoom.Type.PUZZLE);
				room.setPuzzle(RaidRoom.Puzzle.ICE_DEMON);
				break;

			case RAIDS_THIEVING:
				room.setType(RaidRoom.Type.PUZZLE);
				room.setPuzzle(RaidRoom.Puzzle.THIEVING);
				break;

			case RAIDS_FARMING:
			case RAIDS_FARMING2:
				room.setType(RaidRoom.Type.FARMING);
				break;

			case RAIDS_MUTTADILES:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.MUTTADILES);
				break;

			case RAIDS_MYSTICS:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.MYSTICS);
				break;

			case RAIDS_TEKTON:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.TEKTON);
				break;

			case RAIDS_TIGHTROPE:
				room.setType(RaidRoom.Type.PUZZLE);
				room.setPuzzle(RaidRoom.Puzzle.TIGHTROPE);
				break;

			case RAIDS_GUARDIANS:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.GUARDIANS);
				break;

			case RAIDS_CRABS:
				room.setType(RaidRoom.Type.PUZZLE);
				room.setPuzzle(RaidRoom.Puzzle.CRABS);
				break;

			case RAIDS_VESPULA:
				room.setType(RaidRoom.Type.COMBAT);
				room.setBoss(RaidRoom.Boss.VESPULA);
				break;
		}

		return room;
	}

	private BufferedImage getRaidsIcon()
	{
		if (raidsIcon != null)
		{
			return raidsIcon;
		}
		try
		{
			synchronized (ImageIO.class)
			{
				raidsIcon = ImageIO.read(RaidsPlugin.class.getResourceAsStream("raids_icon.png"));
			}
		}
		catch (IOException ex)
		{
			log.warn("Unable to load image", ex);
		}

		return raidsIcon;
	}
}
