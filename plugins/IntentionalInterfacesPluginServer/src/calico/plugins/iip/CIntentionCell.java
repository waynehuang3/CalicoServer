package calico.plugins.iip;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import calico.components.CCanvas;
import calico.networking.netstuff.CalicoPacket;
import calico.plugins.iip.graph.layout.CIntentionLayout;

public class CIntentionCell
{
	public static final String DEFAULT_TITLE = "<default>";

	long uuid;
	long canvas_uuid;
	final Point location;
	String title;
	Long intentionTypeId = null;

	public CIntentionCell(long uuid, long canvasId)
	{
		this.uuid = uuid;
		this.canvas_uuid = canvasId;
		this.location = new Point(-(CIntentionLayout.INTENTION_CELL_SIZE.width / 2), -(CIntentionLayout.INTENTION_CELL_SIZE.height / 2));
		this.title = DEFAULT_TITLE;
	}

	public long getId()
	{
		return uuid;
	}

	public long getCanvasId()
	{
		return canvas_uuid;
	}

	public Point getLocation()
	{
		return location;
	}

	/**
	 * If different than the current location, set the location of the CIC and return true.
	 */
	public boolean setLocation(int x, int y)
	{
		if ((location.x == x) && (location.y == y))
		{
			return false;
		}

		location.x = x;
		location.y = y;

		return true;
	}

	public String getTitle()
	{
		return title;
	}

	public boolean hasUserTitle()
	{
		return !title.equals(DEFAULT_TITLE);
	}

	public void setTitle(String title)
	{
		this.title = title;
	}
	
	public boolean hasIntentionType()
	{
		return (intentionTypeId != null);
	}
	
	public Long getIntentionTypeId()
	{
		return intentionTypeId;
	}

	public void setIntentionType(long intentionTypeId)
	{
		this.intentionTypeId = intentionTypeId;
	}

	public void clearIntentionType()
	{
		intentionTypeId = null;
	}

	public CalicoPacket getCreatePacket()
	{
		return CalicoPacket.getPacket(IntentionalInterfacesNetworkCommands.CIC_CREATE, uuid, canvas_uuid, location.x, location.y, title);
	}

	public void populateState(IntentionalInterfaceState state)
	{
		state.addCellPacket(getCreatePacket());

		if (intentionTypeId != null)
		{
			state.addCellPacket(CalicoPacket.getPacket(IntentionalInterfacesNetworkCommands.CIC_TAG, uuid, intentionTypeId));
		}
	}
}
