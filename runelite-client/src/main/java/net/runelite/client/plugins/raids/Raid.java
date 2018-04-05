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

import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.raids.solver.Layout;
import net.runelite.client.plugins.raids.solver.Room;

public class Raid
{
	public static final int MAX_RAID_ROOMS = 16;

	@Getter
	private final RaidRoom[] rooms = new RaidRoom[MAX_RAID_ROOMS];

	@Getter
	private Layout layout;

	@Getter
	@Setter
	private WorldPoint base;

	public void updateLayout(Layout layout)
	{
		if (layout == null)
		{
			return;
		}

		this.layout = layout;

		for (int i = 0; i < rooms.length; i++)
		{
			if (layout.getRoomAt(i) == null)
			{
				continue;
			}

			RaidRoom room = rooms[i];

			if (room == null)
			{
				RaidRoom.Type type = RaidRoom.Type.fromCode(layout.getRoomAt(i).getSymbol());
				room = new RaidRoom(getBaseFromPosition(i), type);

				if (type == RaidRoom.Type.COMBAT)
				{
					room.setBoss(RaidRoom.Boss.UNKNOWN);
				}

				if (type == RaidRoom.Type.PUZZLE)
				{
					room.setPuzzle(RaidRoom.Puzzle.UNKNOWN);
				}

				setRoom(room, i);
			}

			room.setIndex(layout.getRooms().indexOf(layout.getRoomAt(i)));
		}
	}

	private WorldPoint getBaseFromPosition(int position)
	{
		int x = base.getX();
		int y = base.getY();
		int plane = 3;

		if (position >= 8)
		{
			plane = 2;
			position -= 8;
		}

		if (position >= 4)
		{
			position -= 4;
		}
		else
		{
			y += RaidRoom.ROOM_MAX_SIZE;
		}

		x += RaidRoom.ROOM_MAX_SIZE * position;
		return new WorldPoint(x, y, plane);
	}

	public RaidRoom getRoomAt(int room)
	{
		if (layout == null || room >= layout.getRooms().size())
		{
			return null;
		}

		return rooms[layout.getRooms().get(room).getPosition()];
	}

	public void setRoom(RaidRoom room, int position)
	{
		if (position < rooms.length)
		{
			rooms[position] = room;
		}
	}

	public RaidRoom[] getCombatRooms()
	{
		List<RaidRoom> combatRooms = new ArrayList<>();

		for (Room room : layout.getRooms())
		{
			if (room == null)
			{
				continue;
			}

			if (rooms[room.getPosition()].getType() == RaidRoom.Type.COMBAT)
			{
				combatRooms.add(rooms[room.getPosition()]);
			}
		}

		return combatRooms.toArray(new RaidRoom[combatRooms.size()]);
	}

	public String getRotationString()
	{
		return Joiner.on(",").join(Arrays.stream(getCombatRooms()).map(r -> r.getBoss().getName()).toArray());
	}

	public String toCode()
	{
		StringBuilder builder = new StringBuilder();

		for (RaidRoom room : rooms)
		{
			if (room != null)
			{
				builder.append(room.getType().getCode());
			}
			else
			{
				builder.append(" ");
			}
		}

		return builder.toString();
	}
}
