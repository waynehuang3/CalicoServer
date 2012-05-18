package calico.plugins.iip.controllers;

import it.unimi.dsi.fastutil.longs.Long2ReferenceArrayMap;

import java.util.ArrayList;
import java.util.List;

import calico.plugins.iip.CCanvasLink;
import calico.plugins.iip.CCanvasLinkAnchor;
import calico.plugins.iip.IntentionalInterfaceState;
import calico.plugins.iip.graph.layout.CIntentionLayout;

public class CCanvasLinkController
{
	public static CCanvasLinkController getInstance()
	{
		return INSTANCE;
	}
	
	private static final CCanvasLinkController INSTANCE = new CCanvasLinkController();
	
	private static Long2ReferenceArrayMap<CCanvasLink> links = new Long2ReferenceArrayMap<CCanvasLink>();
	private static Long2ReferenceArrayMap<CCanvasLinkAnchor> linkAnchors = new Long2ReferenceArrayMap<CCanvasLinkAnchor>();
	private static Long2ReferenceArrayMap<List<Long>> anchorIdsByCanvasId = new Long2ReferenceArrayMap<List<Long>>();

	public void populateState(IntentionalInterfaceState state)
	{
		for (CCanvasLink link : links.values())
		{
			state.addLinkPacket(link.getState());
		}
	}
	
	public CCanvasLinkAnchor getAnchor(long anchorId)
	{
		return linkAnchors.get(anchorId);
	}
	
	public CCanvasLink getLink(long linkId)
	{
		return links.get(linkId);
	}
	
	public CCanvasLinkAnchor getOpposite(long anchorId)
	{
		CCanvasLinkAnchor anchor = linkAnchors.get(anchorId);
		CCanvasLink link = links.get(anchor.getLinkId());
		if (link.getAnchorA() == anchor)
		{
			return link.getAnchorB();
		}
		else
		{
			return link.getAnchorA();
		}
	}
	
	public boolean isDestination(long anchorId)
	{
		CCanvasLinkAnchor anchor = linkAnchors.get(anchorId);
		CCanvasLink link = links.get(anchor.getLinkId());
		return (link.getAnchorB() == anchor);
	}

	public void addLink(CCanvasLink link)
	{
		links.put(link.getId(), link);
		
		addLinkAnchor(link.getAnchorA());
		addLinkAnchor(link.getAnchorB());
	}
	
	private void addLinkAnchor(CCanvasLinkAnchor anchor)
	{
		linkAnchors.put(anchor.getId(), anchor);
		getAnchorIdsForCanvasId(anchor.getCanvasId()).add(anchor.getId());
	}
	
	public CCanvasLink getLinkById(long uuid)
	{
		return links.get(uuid);
	}

	public void removeLinkById(long uuid)
	{
		CCanvasLink link = links.remove(uuid);
		removeLinkAnchor(link.getAnchorA());
		removeLinkAnchor(link.getAnchorB());
	}
	
	private void removeLinkAnchor(CCanvasLinkAnchor anchor)
	{
		linkAnchors.remove(anchor.getId());
		getAnchorIdsForCanvasId(anchor.getCanvasId()).remove(anchor.getId());
	}
	
	public List<Long> getAnchorIdsForCanvasId(long canvasId)
	{
		List<Long> anchorIds = anchorIdsByCanvasId.get(canvasId);
		if (anchorIds == null)
		{
			anchorIds = new ArrayList<Long>();
			anchorIdsByCanvasId.put(canvasId, anchorIds);
		}
		return anchorIds;
	}
	
	public void moveLinkAnchor(long anchor_uuid, long canvas_uuid, CCanvasLinkAnchor.Type type, int x, int y)
	{
		CCanvasLinkAnchor anchor = linkAnchors.get(anchor_uuid);
		
		if (anchor.getCanvasId() != canvas_uuid)
		{
			CIntentionLayout.getInstance().populateLayout();
		}
		
		anchor.move(canvas_uuid, type, x, y);
	}
	
	public List<Long> getLinkIdsForCanvas(long canvasId)
	{
		List<Long> linkIds = new ArrayList<Long>();
		for (Long anchorId : getAnchorIdsForCanvasId(canvasId))
		{
				linkIds.add(linkAnchors.get(anchorId).getLinkId());
		}
		return linkIds;
	}
}
