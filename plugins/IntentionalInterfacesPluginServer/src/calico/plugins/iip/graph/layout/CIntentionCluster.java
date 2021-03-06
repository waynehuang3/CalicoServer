package calico.plugins.iip.graph.layout;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import calico.components.CCanvas;
import calico.controllers.CCanvasController;
import calico.plugins.iip.CCanvasLink;
import calico.plugins.iip.CCanvasLinkAnchor;
import calico.plugins.iip.controllers.CCanvasLinkController;

class CIntentionCluster
{
	private static final SliceSorter SLICE_SORTER = new SliceSorter();
	static final int RING_SEPARATION = 80 + CIntentionLayout.INTENTION_CELL_DIAMETER;

	private final List<CIntentionRing> rings = new ArrayList<CIntentionRing>();
	private final Map<Long, CIntentionSlice> slicesByRootCanvasId = new LinkedHashMap<Long, CIntentionSlice>();
	private final long rootCanvasId;

	// transitory values per layout execution
	private final Point location = new Point();
	private final Dimension layoutSize = new Dimension();

	public CIntentionCluster(long rootCanvasId)
	{
		this.rootCanvasId = rootCanvasId;
	}

	long getRootCanvasId()
	{
		return rootCanvasId;
	}

	void describeMaxProjectedSpans(StringBuilder buffer)
	{
		buffer.append("[");
		for (CIntentionRing ring : rings)
		{
			int maxProjectedSpan = 0;
			for (CIntentionSlice slice : slicesByRootCanvasId.values())
			{
				if (slice.getMaxProjectedSpan(ring.getIndex()) > maxProjectedSpan)
				{
					maxProjectedSpan = slice.getMaxProjectedSpan(ring.getIndex());
				}
			}

			buffer.append(ring.getIndex());
			buffer.append(": ");
			buffer.append(maxProjectedSpan);
			buffer.append("; ");
		}
		buffer.append("]");
	}

	void populateCluster()
	{
		for (CIntentionRing ring : rings)
		{
			ring.clear();
		}
		int totalInOrbit = 0;

		List<CIntentionSlice> slices = new ArrayList<CIntentionSlice>();
		for (long anchorId : CCanvasLinkController.getInstance().getAnchorIdsForCanvasId(rootCanvasId))
		{
			long linkedCanvasId = CCanvasLinkController.getInstance().getOpposite(anchorId).getCanvasId();
			if (linkedCanvasId < 0L)
			{
				continue;
			}

			CIntentionSlice slice = new CIntentionSlice(linkedCanvasId);
			slices.add(slice);

			traverseAndPopulate(-1L, linkedCanvasId, 0, slice);
			totalInOrbit += slice.size();
		}

		slicesByRootCanvasId.clear();
		Collections.sort(slices, SLICE_SORTER);
		for (CIntentionSlice slice : slices)
		{
			slicesByRootCanvasId.put(slice.getRootCanvasId(), slice);
		}

		weighSlices(totalInOrbit);
	}

	Dimension getLayoutSize()
	{
		return layoutSize;
	}

	void layoutClusterAsTree(Point clusterCenter, Set<Long> movedCells)
	{
		location.setLocation(clusterCenter);

		int maxProjectedRingSpan = 0;
		for (CIntentionRing ring : rings)
		{
			int maxProjectedSliceSpan = 0;
			for (CIntentionSlice slice : slicesByRootCanvasId.values())
			{
				if (slice.getMaxProjectedSpan(ring.getIndex()) > maxProjectedSliceSpan)
				{
					maxProjectedSliceSpan = slice.getMaxProjectedSpan(ring.getIndex());
				}
			}

			if (maxProjectedSliceSpan > maxProjectedRingSpan)
			{
				maxProjectedRingSpan = maxProjectedSliceSpan;
			}
		}

		if (CIntentionLayout.centerCanvasAt(rootCanvasId, location.x, location.y))
		{
			movedCells.add(rootCanvasId);
		}

		layoutSize.setSize(maxProjectedRingSpan, rings.size() * RING_SEPARATION);

		Point sliceRoot = new Point(location.x, location.y + RING_SEPARATION);
		for (CIntentionSlice slice : slicesByRootCanvasId.values())
		{
			slice.layoutSliceAsTree(sliceRoot, maxProjectedRingSpan, movedCells);
			sliceRoot.x += slice.getLayoutSpan();
		}
	}

	List<Double> getRingRadii()
	{
		List<Double> ringRadii = new ArrayList<Double>();
		double lastRingRadius = 0.0;
		for (CIntentionRing ring : rings)
		{
			int ringSpan = 0;
			for (CIntentionSlice slice : slicesByRootCanvasId.values())
			{
				if (slice.getMaxProjectedSpan(ring.getIndex()) > ringSpan)
				{
					ringSpan = slice.getMaxProjectedSpan(ring.getIndex());
				}
			}

			double ringRadius = ringSpan / (2 * Math.PI);
			if (ringRadius < (lastRingRadius + RING_SEPARATION))
			{
				ringRadius = (lastRingRadius + RING_SEPARATION);
				ringSpan = (int) (2 * Math.PI * ringRadius);
			}

			ringRadii.add(ringRadius);
			lastRingRadius = ringRadius;
		}
		return ringRadii;
	}

	void layoutClusterAsCircles(Point clusterCenter, Set<Long> movedCells, List<Double> ringRadii)
	{
		location.setLocation(clusterCenter);

		if (CIntentionLayout.centerCanvasAt(rootCanvasId, location.x, location.y))
		{
			movedCells.add(rootCanvasId);
		}

		for (int i = 0; i < ringRadii.size(); i++)
		{
			double ringRadius = ringRadii.get(i);
			int ringSpan = (int) (2 * Math.PI * ringRadius);

			int sliceStart = 0;
			CIntentionArcTransformer arcTransformer = null;
			for (CIntentionSlice slice : slicesByRootCanvasId.values())
			{
				if (arcTransformer == null)
				{
					arcTransformer = new CIntentionArcTransformer(location, ringRadius, ringSpan, slice.calculateLayoutSpan(ringSpan));
				}
				slice.layoutArc(arcTransformer, i, ringSpan, sliceStart, movedCells, (i == 0) ? null : ringRadii.get(i - 1));
				sliceStart += slice.getLayoutSpan();
			}
		}

		if (ringRadii.isEmpty())
		{
			layoutSize.setSize(CIntentionLayout.INTENTION_CELL_DIAMETER, CIntentionLayout.INTENTION_CELL_DIAMETER);
		}
		else
		{
			layoutSize.setSize((int) (ringRadii.get(ringRadii.size() - 1) * 2), (int) (ringRadii.get(ringRadii.size() - 1) * 2));
		}
	}

	private void traverseAndPopulate(long parentCanvasId, long canvasId, int ringIndex, CIntentionSlice slice)
	{
		CIntentionRing ring = getRing(ringIndex);
		ring.addCanvas(canvasId);

		slice.addCanvas(parentCanvasId, canvasId, ringIndex);

		for (long anchorId : CCanvasLinkController.getInstance().getAnchorIdsForCanvasId(canvasId))
		{
			CCanvasLinkAnchor anchor = CCanvasLinkController.getInstance().getAnchor(anchorId);
			CCanvasLink link = CCanvasLinkController.getInstance().getLink(anchor.getLinkId());
			if (link.getAnchorB().getId() == anchorId)
			{
				continue;
			}
			long linkedCanvasId = CCanvasLinkController.getInstance().getOpposite(anchorId).getCanvasId();
			if (linkedCanvasId < 0L)
			{
				continue; // this is not a canvas, nothing is here
			}
			traverseAndPopulate(canvasId, linkedCanvasId, ringIndex + 1, slice);
		}
	}

	private void weighSlices(int totalInOrbit)
	{
		for (CIntentionSlice slice : slicesByRootCanvasId.values())
		{
			slice.setPopulationWeight(totalInOrbit);
		}

		double minimumRingRadius = 0.0;
		double equalSliceWeight = 1.0 / (double) slicesByRootCanvasId.size();
		for (CIntentionRing ring : rings)
		{
			minimumRingRadius += RING_SEPARATION;
			double minimumRingSpan = 2 * Math.PI * minimumRingRadius;
			int maxCellsInMinRingSpan = (int) (minimumRingSpan / CIntentionLayout.INTENTION_CELL_DIAMETER);
			boolean ringCrowded = ring.size() > maxCellsInMinRingSpan;

			int maxCellsInEqualSliceSpan = (maxCellsInMinRingSpan / slicesByRootCanvasId.size());
			boolean equalSlicesCrowded = false;
			for (CIntentionSlice slice : slicesByRootCanvasId.values())
			{
				if (slice.arcSize(ring.getIndex()) > maxCellsInEqualSliceSpan)
				{
					equalSlicesCrowded = true;
					break;
				}
			}

			for (CIntentionSlice slice : slicesByRootCanvasId.values())
			{
				double arcWeight;
				if (ringCrowded)
				{
					arcWeight = slice.arcSize(ring.getIndex()) / (double) ring.size();
				}
				else if (equalSlicesCrowded)
				{
					arcWeight = (slice.arcSize(ring.getIndex()) * CIntentionLayout.INTENTION_CELL_DIAMETER) / minimumRingSpan;
				}
				else
				{
					arcWeight = equalSliceWeight;
				}
				slice.setArcWeight(ring.getIndex(), arcWeight);
			}
		}

		double sumOfMaxWeights = 0.0;
		for (CIntentionSlice slice : slicesByRootCanvasId.values())
		{
			slice.calculateMaxArcWeight();
			sumOfMaxWeights += slice.getMaxArcWeight();
		}

		double reductionRatio = 1.0 / Math.max(1.0, sumOfMaxWeights);
		for (CIntentionSlice slice : slicesByRootCanvasId.values())
		{
			slice.setWeight(slice.getMaxArcWeight() * reductionRatio);
		}

		// what percentage of the minimum ring span is occupied by slice a? If it is less than the weighted percentage,
		// then it only needs that much.

		// Distributions:
		// 1. weighted
		// 2. equal
		// 3. by occupancy at minimum ring size

		// the idea is to choose a distribution per ring, normalize each one, and then balance maximi per slice
	}

	private CIntentionRing getRing(int ringIndex)
	{
		for (int i = rings.size(); i <= ringIndex; i++)
		{
			rings.add(new CIntentionRing(i));
		}
		return rings.get(ringIndex);
	}

	private static class SliceSorter implements Comparator<CIntentionSlice>
	{
		public int compare(CIntentionSlice first, CIntentionSlice second)
		{
			CCanvas firstCanvas = CCanvasController.canvases.get(first.getRootCanvasId());
			CCanvas secondCanvas = CCanvasController.canvases.get(second.getRootCanvasId());

			return firstCanvas.getIndex() - secondCanvas.getIndex();
		}
	}
}
