package calico.plugins.iip;

import java.util.Set;

import calico.clients.Client;
import calico.clients.ClientManager;
import calico.controllers.CCanvasController;
import calico.events.CalicoEventHandler;
import calico.events.CalicoEventListener;
import calico.networking.netstuff.CalicoPacket;
import calico.networking.netstuff.NetworkCommand;
import calico.plugins.AbstractCalicoPlugin;
import calico.plugins.CalicoPluginManager;
import calico.plugins.CalicoStateElement;
import calico.plugins.iip.controllers.CCanvasLinkController;
import calico.plugins.iip.controllers.CIntentionCellController;
import calico.plugins.iip.graph.layout.CIntentionLayout;
import calico.uuid.UUIDAllocator;

public class IntentionalInterfacesServerPlugin extends AbstractCalicoPlugin implements CalicoEventListener, CalicoStateElement
{
	private final IntentionalInterfaceState state = new IntentionalInterfaceState();

	public IntentionalInterfacesServerPlugin()
	{
		PluginInfo.name = "Intentional Interfaces";
	}

	public void onPluginStart()
	{
		CalicoEventHandler.getInstance().addListener(NetworkCommand.CANVAS_CREATE, this, CalicoEventHandler.PASSIVE_LISTENER);
		CalicoEventHandler.getInstance().addListener(NetworkCommand.CANVAS_DELETE, this, CalicoEventHandler.PASSIVE_LISTENER);
		CalicoEventHandler.getInstance().addListener(NetworkCommand.RESTORE_START, this, CalicoEventHandler.PASSIVE_LISTENER);

		for (Integer event : this.getNetworkCommands())
		{
			System.out.println("IntentionalInterfacesPlugin: attempting to listen for " + event.intValue());
			CalicoEventHandler.getInstance().addListener(event.intValue(), this, CalicoEventHandler.ACTION_PERFORMER_LISTENER);
		}

		// create the default intention types
		CIntentionCellController.getInstance().createIntentionType(UUIDAllocator.getUUID(), "New Perspective", 0);
		CIntentionCellController.getInstance().createIntentionType(UUIDAllocator.getUUID(), "New Alternative", 1);
		CIntentionCellController.getInstance().createIntentionType(UUIDAllocator.getUUID(), "New Idea", 2);
		CIntentionCellController.getInstance().createIntentionType(UUIDAllocator.getUUID(), "Design Inside", 3);

		CalicoPluginManager.registerCalicoStateExtension(this);

		for (long canvasId : CCanvasController.canvases.keySet())
		{
			createIntentionCell(canvasId);
		}
	}

	@Override
	public void handleCalicoEvent(int event, CalicoPacket p, Client c)
	{
		if (event == NetworkCommand.RESTORE_START)
		{
			clearState();
			return;
		}
		
		if (IntentionalInterfacesNetworkCommands.Command.isInDomain(event))
		{
			switch (IntentionalInterfacesNetworkCommands.Command.forId(event))
			{
				case CIC_CREATE:
					CIC_CREATE(p, c);
					break;
				case CIC_MOVE:
					CIC_MOVE(p, c);
					break;
				case CIC_SET_TITLE:
					CIC_SET_TITLE(p, c, true);
					break;
				case CIC_TAG:
					CIC_TAG(p, c);
					break;
				case CIC_UNTAG:
					CIC_UNTAG(p, c, true);
					break;
				case CIC_DELETE:
					CIC_DELETE(p, c);
					break;
				case CIT_CREATE:
					CIT_CREATE(p, c);
					break;
				case CIT_RENAME:
					CIT_RENAME(p, c);
					break;
				case CIT_SET_COLOR:
					CIT_SET_COLOR(p, c);
					break;
				case CIT_DELETE:
					CIT_DELETE(p, c);
					break;
				case CLINK_CREATE:
					CLINK_CREATE(p, c);
					break;
				case CLINK_MOVE_ANCHOR:
					CLINK_MOVE_ANCHOR(p, c);
					break;
				case CLINK_LABEL:
					CLINK_LABEL(p, c);
					break;
				case CLINK_DELETE:
					CLINK_DELETE(p, c, true);
					break;
			}
		}
		else
		{
			p.rewind();
			p.getInt();
			long canvasId = p.getLong();

			switch (event)
			{
				case NetworkCommand.CANVAS_CREATE:
					createIntentionCell(canvasId);
					break;
				case NetworkCommand.CANVAS_DELETE:
					CANVAS_DELETE(p, c, canvasId);
					break;
			}
		}
	}

	private static void createIntentionCell(long canvasId)
	{
		CIntentionCell cell = new CIntentionCell(UUIDAllocator.getUUID(), canvasId);
		CIntentionCellController.getInstance().addCell(cell);

		CIntentionLayout.getInstance().insertNewCluster(cell);

		CalicoPacket p = cell.getCreatePacket();
		forward(p);
	}
	
	private static void clearState()
	{
		CIntentionCellController.getInstance().clearState();
		CCanvasLinkController.getInstance().clearState();
	}

	// this is called only during restore
	private static void CIC_CREATE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_CREATE.verify(p);
		
		long uuid = p.getLong();
		long canvasId = p.getLong();
		int x = p.getInt();
		int y = p.getInt();
		String title = p.getString();
		
		CIntentionCell cell = new CIntentionCell(uuid, canvasId);
		cell.setLocation(x, y);
		cell.setTitle(title);

		CIntentionCellController.getInstance().addCell(cell);
		CIntentionLayout.getInstance().insertNewCluster(cell);
	}
	
	private static void CANVAS_DELETE(CalicoPacket p, Client c, long canvasId)
	{
		CIntentionCell cell = CIntentionCellController.getInstance().getCellByCanvasId(canvasId);
		CIntentionCellController.getInstance().removeCellById(cell.getId());
		deleteAllLinks(canvasId, true);
		
		CalicoPacket cicDelete = CalicoPacket.getPacket(IntentionalInterfacesNetworkCommands.CIC_DELETE, cell.getId());
		forward(cicDelete);
		
		layoutGraph();
	}
	
	private static void deleteAllLinks(long canvasId, boolean forward)
	{
		for (Long linkId : CCanvasLinkController.getInstance().getLinkIdsForCanvas(canvasId))
		{
			CCanvasLinkController.getInstance().removeLinkById(linkId);
			
			if (forward)
			{
				CalicoPacket packet = new CalicoPacket();
				packet.putInt(IntentionalInterfacesNetworkCommands.CLINK_DELETE);
				packet.putLong(linkId);
				forward(packet);
			}
		}
	}

	private static void CIC_MOVE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_MOVE.verify(p);

		long uuid = p.getLong();
		CIntentionCell cell = CIntentionCellController.getInstance().getCellById(uuid);

		int x = p.getInt();
		int y = p.getInt();
		cell.setLocation(x, y);

		forward(p, c);
	}

	private static void CIC_SET_TITLE(CalicoPacket p, Client c, boolean forward)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_SET_TITLE.verify(p);

		long uuid = p.getLong();
		CIntentionCell cell = CIntentionCellController.getInstance().getCellById(uuid);

		cell.setTitle(p.getString());

		if (forward)
		{
			forward(p, c);
		}
	}

	private static void CIC_TAG(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_TAG.verify(p);

		long uuid = p.getLong();
		long typeId = p.getLong();

		CIntentionCell cell = CIntentionCellController.getInstance().getCellById(uuid);
		cell.setIntentionType(typeId);

		forward(p, c);
	}

	private static void CIC_UNTAG(CalicoPacket p, Client c, boolean forward)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_UNTAG.verify(p);

		long uuid = p.getLong();
		long typeId = p.getLong();

		CIntentionCell cell = CIntentionCellController.getInstance().getCellById(uuid);
		cell.clearIntentionType();

		if (forward)
		{
			forward(p, c);
		}
	}

	private static void CIC_DELETE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_DELETE.verify(p);

		long uuid = p.getLong();
		CIntentionCellController.getInstance().removeCellById(uuid);

		layoutGraph();

		forward(p, c);
	}

	private static void CIT_CREATE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIT_CREATE.verify(p);

		long uuid = p.getLong();
		String name = p.getString();
		int colorIndex = p.getInt();

		CIntentionType type = CIntentionCellController.getInstance().createIntentionType(uuid, name, colorIndex);

		CalicoPacket colored = CalicoPacket.getPacket(IntentionalInterfacesNetworkCommands.CIT_CREATE, uuid, name, type.getColorIndex());
		ClientManager.send(colored);
	}

	private static void CIT_RENAME(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIT_RENAME.verify(p);

		long uuid = p.getLong();
		String name = p.getString();
		CIntentionCellController.getInstance().renameIntentionType(uuid, name);

		forward(p, c);
	}

	private static void CIT_SET_COLOR(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIT_SET_COLOR.verify(p);

		long uuid = p.getLong();
		int color = p.getInt();
		CIntentionCellController.getInstance().setIntentionTypeColor(uuid, color);

		forward(p, c);
	}

	private static void CIT_DELETE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIT_DELETE.verify(p);

		long uuid = p.getLong();

		CIntentionCellController.getInstance().removeIntentionType(uuid);

		forward(p, c);
	}

	private static CCanvasLinkAnchor unpackAnchor(long link_uuid, CalicoPacket p)
	{
		long uuid = p.getLong();
		long canvas_uuid = p.getLong();
		CCanvasLinkAnchor.Type type = CCanvasLinkAnchor.Type.values()[p.getInt()];
		int x = p.getInt();
		int y = p.getInt();
		long group_uuid = p.getLong();
		return new CCanvasLinkAnchor(uuid, link_uuid, canvas_uuid, type, x, y, group_uuid);
	}

	private static void CLINK_CREATE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CLINK_CREATE.verify(p);

		long uuid = p.getLong();
		CCanvasLinkAnchor anchorA = unpackAnchor(uuid, p);
		CCanvasLinkAnchor anchorB = unpackAnchor(uuid, p);
		
		if (!(CCanvasController.canvases.containsKey(anchorA.getCanvasId()) && CCanvasController.canvases.containsKey(anchorB.getCanvasId())))
		{
			// the canvas has been deleted
			return;
		}
		
		Long incomingLinkId = CCanvasLinkController.getInstance().getIncomingLink(anchorB.getCanvasId());
		if (incomingLinkId != null)
		{
			CCanvasLinkController.getInstance().removeLinkById(incomingLinkId);
			CalicoPacket deleteIncoming = CalicoPacket.getPacket(IntentionalInterfacesNetworkCommands.CLINK_DELETE, incomingLinkId);
			forward(deleteIncoming);
		}
		
		CCanvasLink link = new CCanvasLink(uuid, anchorA, anchorB);
		CCanvasLinkController.getInstance().addLink(link);

		layoutGraph();

		forward(p, c);
	}

	private static void CLINK_MOVE_ANCHOR(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CLINK_MOVE_ANCHOR.verify(p);

		long anchor_uuid = p.getLong();
		long canvas_uuid = p.getLong();
		CCanvasLinkAnchor.Type type = CCanvasLinkAnchor.Type.values()[p.getInt()];
		int x = p.getInt();
		int y = p.getInt();

		CCanvasLinkController.getInstance().moveLinkAnchor(anchor_uuid, canvas_uuid, type, x, y);

		forward(p, c);
	}

	private static void CLINK_LABEL(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CLINK_LABEL.verify(p);

		long uuid = p.getLong();
		CCanvasLink link = CCanvasLinkController.getInstance().getLinkById(uuid);
		link.setLabel(p.getString());

		forward(p, c);
	}

	private static void CLINK_DELETE(CalicoPacket p, Client c, boolean forward)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CLINK_DELETE.verify(p);

		long uuid = p.getLong();
		CCanvasLinkController.getInstance().removeLinkById(uuid);

		layoutGraph();

		if (forward)
		{
			forward(p, c);
		}
	}

	public static void layoutGraph()
	{
		CIntentionLayout.getInstance().populateLayout();
		CIntentionLayout.getInstance().layoutGraph();
		Set<Long> movedCells = CIntentionLayout.getInstance().getMovedCells();
		
		for (CIntentionCell cell : CIntentionCellController.getInstance().getAllCells())
		{
			if (movedCells.contains(cell.getCanvasId()))
			{
				CalicoPacket p = new CalicoPacket();
				p.putInt(IntentionalInterfacesNetworkCommands.CIC_MOVE);
				p.putLong(cell.getId());
				p.putInt(cell.getLocation().x);
				p.putInt(cell.getLocation().y);
				forward(p);
			}
		}
		
		forward(CIntentionLayout.getInstance().getTopology().createPacket());
	}

	private static void forward(CalicoPacket p)
	{
		forward(p, null);
	}

	private static void forward(CalicoPacket p, Client c)
	{
		if (c == null)
		{
			ClientManager.send(p);
		}
		else
		{
			ClientManager.send_except(c, p);
		}
	}

	@Override
	public CalicoPacket[] getCalicoStateElementUpdatePackets()
	{
		state.reset();
		CIntentionCellController.getInstance().populateState(state);
		CCanvasLinkController.getInstance().populateState(state);
		CIntentionLayout.getInstance().populateState(state);

		return state.getAllPackets();
	}

	public Class<?> getNetworkCommandsClass()
	{
		return IntentionalInterfacesNetworkCommands.class;
	}
}