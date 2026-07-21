'use client';

import { useState, useEffect } from 'react';

interface Transaction {
  id: string;
  senderAccount: string;
  receiverAccount: string;
  amount: number;
  currency: string;
  country: string;
  timestamp: string;
  status: string;
}

interface Alert {
  id: string;
  transactionId: string;
  severity: string;
  reason: string;
  status: string;
  createdAt: string;
}

interface Stats {
  totalTransactions: number;
  openAlerts: number;
  totalAlerts: number;
  totalSars: number;
  averageRiskScore: number;
}

interface GraphData {
  nodes: { id: string; label: string }[];
  links: { source: string; target: string; amount: number; id: string }[];
  alerts: string[];
}

export default function Dashboard() {
  const [stats, setStats] = useState<Stats>({
    totalTransactions: 0,
    openAlerts: 0,
    totalAlerts: 0,
    totalSars: 0,
    averageRiskScore: 0,
  });
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [graphData, setGraphData] = useState<GraphData>({ nodes: [], links: [], alerts: [] });
  const [selectedAlert, setSelectedAlert] = useState<Alert | null>(null);
  const [sarText, setSarText] = useState<string>('');
  const [shapExplanations, setShapExplanations] = useState<any[]>([]);
  const [loadingSar, setLoadingSar] = useState(false);
  const [selectedCycleIndex, setSelectedCycleIndex] = useState(0);
  const [hoveredGraphItem, setHoveredGraphItem] = useState<{
    type: 'node' | 'link' | 'cycle';
    title: string;
    details: string[];
  } | null>(null);

  // Ingest Form State
  const [newTx, setNewTx] = useState({
    senderAccount: 'ACC-001',
    receiverAccount: 'ACC-003',
    amount: 15000,
    currency: 'USD',
    country: 'HighRiskCountry',
  });

  const backendUrl = 'http://localhost:8080';

  // Map internal status codes to user-friendly labels and accessible color classes
  const getStatusDisplay = (status: string) => {
    const map: Record<string, { label: string; className: string }> = {
      PENDING_ML_SCORE: { label: 'Pending ML Score', className: 'bg-amber-100 text-amber-700 border border-amber-100' },
      APPROVED: { label: 'Approved', className: 'bg-green-100 text-green-600 border border-green-100' },
      HOLD: { label: 'Hold', className: 'bg-red-100 text-red-600 border border-red-100' },
      ESCALATE: { label: 'Escalated', className: 'bg-orange-100 text-orange-600 border border-orange-100' },
    };
    if (!status) return { label: 'Unknown', className: 'bg-gray-100 text-gray-700 border border-gray-100' };
    const found = map[status];
    if (found) return found;
    // Fall back to prettified text
    const pretty = status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
    return { label: pretty, className: 'bg-gray-100 text-gray-700 border border-gray-100' };
  };

  const fetchData = async () => {
    try {
      const statsRes = await fetch(`${backendUrl}/api/dashboard/stats`);
      if (statsRes.ok) setStats(await statsRes.json());

      const txRes = await fetch(`${backendUrl}/api/transactions`);
      if (txRes.ok) setTransactions(await txRes.json());

      const alertsRes = await fetch(`${backendUrl}/api/dashboard/alerts`);
      if (alertsRes.ok) setAlerts(await alertsRes.json());

      const graphRes = await fetch(`${backendUrl}/api/dashboard/graph`);
      if (graphRes.ok) setGraphData(await graphRes.json());
    } catch (e) {
      console.error('Failed to fetch dashboard data', e);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 5000);
    return () => clearInterval(interval);
  }, []);

  const handleInjestTransaction = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await fetch(`${backendUrl}/api/transactions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newTx),
      });
      if (res.ok) {
        alert('Transaction Ingested Successfully!');
        fetchData();
      }
    } catch (e) {
      alert('Ingestion failed');
    }
  };

  const handleViewAlert = async (alert: Alert) => {
    setSelectedAlert(alert);
    setLoadingSar(true);
    setSarText('');
    setShapExplanations([]);
    try {
      // Fetch SAR report
      const sarRes = await fetch(`${backendUrl}/api/dashboard/alerts/${alert.id}/sar`);
      if (sarRes.ok) {
        const sar = await sarRes.json();
        setSarText(sar.reportText);
      }

      // Fetch Transaction Details & SHAP Explanations
      const detailsRes = await fetch(`${backendUrl}/api/dashboard/transaction/${alert.transactionId}/details`);
      if (detailsRes.ok) {
        const details = await detailsRes.json();
        if (details.explanations) {
          setShapExplanations(details.explanations);
        }
      }
    } catch (e) {
      console.error('Error fetching alert details', e);
    } finally {
      setLoadingSar(false);
    }
  };

  // Prepare graph layout and fallback links
  const computePositions = (nodes: { id: string; label: string }[]) => {
    const positions: Record<string, { x: number; y: number }> = {};
    if (!nodes || nodes.length === 0) return positions;
    const w = 700;
    const h = 320;
    const cx = w / 2;
    const cy = h / 2;
    const radius = Math.min(200, 50 + nodes.length * 8);
    nodes.forEach((node, i) => {
      const angle = (i / nodes.length) * Math.PI * 2;
      const x = Math.round(cx + Math.cos(angle) * radius);
      const y = Math.round(cy + Math.sin(angle) * radius);
      positions[node.id] = { x, y };
    });
    return positions;
  };

  const linksToRender = (() => {
    if (graphData.links && graphData.links.length > 0) return graphData.links;
    // Fallback: build edges from recent transactions
    const map = new Map<string, { source: string; target: string; amount: number; id: string }>();
    transactions.forEach((t) => {
      const key = `${t.senderAccount}||${t.receiverAccount}`;
      const existing = map.get(key) || { source: t.senderAccount, target: t.receiverAccount, amount: 0, id: key };
      existing.amount += t.amount || 0;
      map.set(key, existing);
    });
    return Array.from(map.values());
  })();
  // Find nodes that are part of cycles (simple DFS on directed edges)
  // Find cycles and cycle participant nodes; returns Set of nodes and list of cycles (each cycle: array of node ids)
  const findCycles = (links: { source: string; target: string; id?: string }[]) => {
    const adj: Record<string, string[]> = {};
    links.forEach((l) => {
      adj[l.source] = adj[l.source] || [];
      adj[l.source].push(l.target);
    });
    const visited: Record<string, boolean> = {};
    const onStack: Record<string, boolean> = {};
    const cycles: string[][] = [];
    const inCycle = new Set<string>();

    const dfs = (node: string, path: string[]) => {
      visited[node] = true;
      onStack[node] = true;
      path.push(node);
      const neighbors = adj[node] || [];
      for (const nb of neighbors) {
        if (!visited[nb]) {
          dfs(nb, path);
        } else if (onStack[nb]) {
          const idx = path.indexOf(nb);
          if (idx >= 0) {
            const cycle = path.slice(idx).concat([nb]);
            // normalize cycle representation (start at smallest id) to avoid duplicates
            const norm = normalizeCycle(cycle);
            // avoid duplicates
            if (!cycles.some((c) => arraysEqual(c, norm))) {
              cycles.push(norm);
              norm.forEach((n) => inCycle.add(n));
            }
          }
        }
      }
      path.pop();
      onStack[node] = false;
    };

    Object.keys(adj).forEach((n) => {
      if (!visited[n]) dfs(n, []);
    });
    return { inCycle, cycles };
  };

  const arraysEqual = (a: string[], b: string[]) => {
    if (a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
    return true;
  };

  const normalizeCycle = (cycle: string[]) => {
    // cycle e.g. [A,B,C,A] -> collapse final dup, rotate so smallest id is first, and keep closing node
    if (cycle.length <= 1) return cycle;
    const unique = cycle.slice(0, cycle.length - 1);
    // find index of lexicographically smallest id
    let minIdx = 0;
    for (let i = 1; i < unique.length; i++) if (unique[i] < unique[minIdx]) minIdx = i;
    const rotated = unique.slice(minIdx).concat(unique.slice(0, minIdx));
    rotated.push(rotated[0]);
    return rotated;
  };

  const { inCycle: cycleNodeSet, cycles } = findCycles(linksToRender);
  const cycleNodes = graphData.nodes.filter((node) => cycleNodeSet.has(node.id));
  const cycleLinks = linksToRender.filter((link) => cycleNodeSet.has(link.source) && cycleNodeSet.has(link.target));

  const activeCycle = cycles.length > 0 ? cycles[Math.min(selectedCycleIndex, cycles.length - 1)] : [];
  const activeCycleNodeSet = new Set(activeCycle.slice(0, -1));
  const activeCycleNodes = cycleNodes.filter((node) => activeCycleNodeSet.has(node.id));
  const activeCycleLinks = cycleLinks.filter((link) => activeCycleNodeSet.has(link.source) && activeCycleNodeSet.has(link.target));
  const activeNodePositions = computePositions(activeCycleNodes || []);

  const getLinkPath = (
    source: { x: number; y: number },
    target: { x: number; y: number },
    index: number,
    hasReversePair: boolean
  ) => {
    const dx = target.x - source.x;
    const dy = target.y - source.y;
    const distance = Math.sqrt(dx * dx + dy * dy) || 1;
    const midX = (source.x + target.x) / 2;
    const midY = (source.y + target.y) / 2;
    const perpendicularX = -dy / distance;
    const perpendicularY = dx / distance;
    const directionSign = index % 2 === 0 ? 1 : -1;
    const curveStrength = hasReversePair ? 32 : 18;
    const offset = curveStrength * directionSign;
    const controlX = midX + perpendicularX * offset;
    const controlY = midY + perpendicularY * offset;
    return `M ${source.x} ${source.y} Q ${controlX} ${controlY} ${target.x} ${target.y}`;
  };

  const activeCycleSummary = activeCycle.length > 1
    ? activeCycle.slice(0, -1).join(' → ') + ` → ${activeCycle[0]}`
    : 'Select a cycle';

  useEffect(() => {
    if (selectedCycleIndex >= cycles.length) {
      setSelectedCycleIndex(0);
    }
  }, [cycles.length, selectedCycleIndex]);

  const getNodeHoverDetails = (nodeId: string) => {
    const nodeTransactions = transactions.filter((tx) => tx.senderAccount === nodeId || tx.receiverAccount === nodeId);
    const inCycleCount = activeCycleLinks.filter((link) => link.source === nodeId || link.target === nodeId).length;
    return {
      title: nodeId,
      details: [
        `Role: ${activeCycleNodeSet.has(nodeId) ? 'Cycle participant' : 'Observed node'}`,
        `Transactions observed: ${nodeTransactions.length}`,
        `Cycle edges touching node: ${inCycleCount}`,
        `Graph label: ${graphData.nodes.find((n) => n.id === nodeId)?.label || nodeId}`,
      ],
    };
  };

  const getLinkHoverDetails = (link: { source: string; target: string; amount: number }) => {
    const reverseExists = activeCycleLinks.some((other) => other.source === link.target && other.target === link.source);
    const pairTransactions = transactions.filter(
      (tx) => tx.senderAccount === link.source && tx.receiverAccount === link.target
    );
    return {
      title: `${link.source} → ${link.target}`,
      details: [
        `Total amount: ${link.amount.toLocaleString()}`,
        `Transactions in pair: ${pairTransactions.length}`,
        `Reverse edge present: ${reverseExists ? 'Yes' : 'No'}`,
        `Direction: sender ${link.source}, receiver ${link.target}`,
      ],
    };
  };

  return (
    <div className="min-h-screen bg-white text-gray-900 font-sans">
      {/* Header */}
      <header className="border-b border-gray-200 bg-white px-6 py-4 flex justify-between items-center sticky top-0 z-50">
        <div className="flex items-center gap-3">
          <div className="h-4 w-4 rounded-full bg-red-500 animate-pulse"></div>
          <h1 className="text-xl font-bold tracking-tight bg-gradient-to-r from-red-500 via-orange-400 to-yellow-300 bg-clip-text text-transparent">
            Aegis // Real-Time Financial Crime Platform
          </h1>
        </div>
        <div className="text-sm text-gray-500">Live Streaming Data Active</div>
      </header>

      <main className="p-6 space-y-6 max-w-[1600px] mx-auto">
        {/* Stats Grid */}
        <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
          <div className="bg-white border border-gray-200 p-4 rounded-xl">
            <div className="text-gray-500 text-xs uppercase tracking-wider">Total Transactions</div>
            <div className="text-3xl font-extrabold mt-1">{stats.totalTransactions}</div>
          </div>
          <div className="bg-white border border-gray-200 p-4 rounded-xl">
            <div className="text-red-500 text-xs uppercase tracking-wider">Open Alerts</div>
            <div className="text-3xl font-extrabold mt-1 text-red-600">{stats.openAlerts}</div>
          </div>
          <div className="bg-white border border-gray-200 p-4 rounded-xl">
            <div className="text-gray-500 text-xs uppercase tracking-wider">Total Alerts</div>
            <div className="text-3xl font-extrabold mt-1">{stats.totalAlerts}</div>
          </div>
          <div className="bg-white border border-gray-200 p-4 rounded-xl">
            <div className="text-gray-500 text-xs uppercase tracking-wider">Generated SARs</div>
            <div className="text-3xl font-extrabold mt-1 text-orange-500">{stats.totalSars}</div>
          </div>
          <div className="bg-white border border-gray-200 p-4 rounded-xl">
            <div className="text-gray-500 text-xs uppercase tracking-wider">Avg Risk Score</div>
            <div className="text-3xl font-extrabold mt-1 text-yellow-500">
              {stats.averageRiskScore.toFixed(1)}%
            </div>
          </div>
        </div>

        {/* Action / Input & Live Feed grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Simulator Form */}
          <div className="bg-white border border-gray-200 p-5 rounded-xl space-y-4">
            <h2 className="text-lg font-bold text-gray-900">Simulate Real-Time Transaction</h2>
            <form onSubmit={handleInjestTransaction} className="space-y-3">
              <div>
                <label className="text-xs text-gray-500 block mb-1">Sender Account</label>
                <input
                  type="text"
                  value={newTx.senderAccount}
                  onChange={(e) => setNewTx({ ...newTx, senderAccount: e.target.value })}
                  className="w-full bg-white border border-gray-200 rounded p-2 text-gray-900 focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="text-xs text-gray-500 block mb-1">Receiver Account</label>
                <input
                  type="text"
                  value={newTx.receiverAccount}
                  onChange={(e) => setNewTx({ ...newTx, receiverAccount: e.target.value })}
                  className="w-full bg-white border border-gray-200 rounded p-2 text-gray-900 focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="text-xs text-gray-500 block mb-1">Amount</label>
                <input
                  type="number"
                  value={newTx.amount}
                  onChange={(e) => setNewTx({ ...newTx, amount: Number(e.target.value) })}
                  className="w-full bg-white border border-gray-200 rounded p-2 text-gray-900 focus:outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="text-xs text-gray-500 block mb-1">Country</label>
                <input
                  type="text"
                  value={newTx.country}
                  onChange={(e) => setNewTx({ ...newTx, country: e.target.value })}
                  className="w-full bg-white border border-gray-200 rounded p-2 text-gray-900 focus:outline-none focus:border-blue-500"
                />
              </div>
              <button
                type="submit"
                className="w-full bg-blue-600 hover:bg-blue-500 text-white font-bold py-2 rounded transition-all mt-4"
              >
                Trigger Transaction
              </button>
            </form>
          </div>

          {/* Live Ingestion Stream */}
          <div className="bg-white border border-gray-200 p-5 rounded-xl lg:col-span-2 flex flex-col h-[400px]">
            <h2 className="text-lg font-bold text-gray-900 mb-3 flex items-center justify-between">
              <span>Live Ingestion Feed</span>
              <span className="text-xs text-green-400 font-normal px-2 py-0.5 rounded bg-green-500/10">
                Streaming
              </span>
            </h2>
            <div className="overflow-y-auto flex-1 space-y-2 pr-2">
              {transactions.length === 0 ? (
                <div className="text-gray-500 text-center py-20">No transactions ingested yet.</div>
              ) : (
                transactions.slice().reverse().map((tx) => (
                  <div
                    key={tx.id}
                    className="p-3 bg-white border border-gray-100 rounded-lg flex items-center justify-between text-sm"
                  >
                    <div>
                      <span className="font-mono text-gray-400 mr-3">{tx.id.substring(0, 8)}...</span>
                      <span className="font-bold">{tx.senderAccount}</span>
                      <span className="text-gray-400 mx-2">→</span>
                      <span className="font-bold">{tx.receiverAccount}</span>
                    </div>
                    <div className="flex items-center gap-4">
                      <span className="font-mono text-gray-700">
                        {tx.amount.toLocaleString()} {tx.currency}
                      </span>
                      {(() => {
                        const s = getStatusDisplay(tx.status);
                        return (
                          <span className={`px-2 py-0.5 rounded text-xs font-semibold ${s.className}`}>
                            {s.label}
                          </span>
                        );
                      })()}
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        {/* Network & Alerts grid */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Action Alerts */}
          <div className="bg-white border border-gray-200 p-5 rounded-xl h-[450px] flex flex-col">
            <h2 className="text-lg font-bold text-red-500 mb-3">Compliance Alerts</h2>
            <div className="overflow-y-auto flex-1 space-y-2 pr-2">
              {alerts.length === 0 ? (
                <div className="text-gray-500 text-center py-20">No alerts triggered yet.</div>
              ) : (
                alerts.slice().reverse().map((alert) => (
                  <div
                    key={alert.id}
                    onClick={() => handleViewAlert(alert)}
                    className="p-3 bg-white hover:bg-gray-50 border border-gray-100 rounded-lg cursor-pointer transition-all flex flex-col gap-1"
                  >
                    <div className="flex justify-between items-center">
                      <span className="text-xs font-mono text-red-600 font-bold">
                        {alert.severity} RISK
                      </span>
                      <span className="text-xs text-gray-500">
                        {new Date(alert.createdAt).toLocaleTimeString()}
                      </span>
                    </div>
                    <p className="text-sm font-semibold">{alert.reason}</p>
                    <span className="text-xs text-gray-400 mt-1">
                      Tx: {alert.transactionId.substring(0, 8)}...
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Network Graph Analytics */}
          <div className="relative overflow-hidden rounded-2xl border border-slate-200 bg-gradient-to-br from-white via-slate-50 to-blue-50/50 p-5 shadow-sm lg:col-span-2 flex flex-col h-[450px]">
            <div className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-red-500 via-orange-400 to-blue-500" />
            <div className="flex items-start justify-between gap-4 mb-1">
              <div>
                <h2 className="text-lg font-bold text-slate-900">Graph Network Discovery</h2>
                <p className="text-xs text-slate-500 mt-1">
                  Only detected cycles are shown. Arrows indicate transaction direction.
                </p>
              </div>
              <div className="flex items-center gap-2 text-[11px] font-semibold">
                <span className="rounded-full border border-red-200 bg-red-50 px-2.5 py-1 text-red-700">
                  Cycle Only
                </span>
                <span className="rounded-full border border-blue-200 bg-blue-50 px-2.5 py-1 text-blue-700">
                  Directed Arrows
                </span>
              </div>
            </div>
            <div className="mt-3 flex-1 rounded-xl border border-slate-200/80 bg-white/85 shadow-inner relative flex items-center justify-center overflow-hidden">
              {graphData.nodes.length === 0 ? (
                <div className="text-slate-500 text-sm">No transaction network loaded.</div>
              ) : (
                <div className="absolute inset-0 p-4 overflow-auto flex flex-col justify-between">
                  <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-slate-500 uppercase tracking-widest font-mono">
                    <span>Cycle Nodes: {activeCycleNodes.length} | Cycle Edges: {activeCycleLinks.length}</span>
                    <span className="text-red-500">Cycles Detected: {cycles.length}</span>
                    {cycles.length > 1 && (
                      <label className="ml-auto flex items-center gap-2 normal-case tracking-normal text-slate-600">
                        <span className="text-[10px] font-semibold uppercase tracking-widest text-slate-500">Cycle</span>
                        <select
                          value={selectedCycleIndex}
                          onChange={(e) => setSelectedCycleIndex(Number(e.target.value))}
                          className="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700 shadow-sm outline-none focus:border-blue-400"
                        >
                          {cycles.map((cycle, idx) => {
                            const cycleLabel = cycle.slice(0, -1).join(' → ');
                            return (
                              <option key={`${idx}-${cycleLabel}`} value={idx}>
                                {idx + 1}. {cycleLabel}
                              </option>
                            );
                          })}
                        </select>
                      </label>
                    )}
                  </div>

                  <div className="mt-2 rounded-lg border border-slate-200 bg-slate-50/70 px-3 py-2 text-[11px] text-slate-600 shadow-sm">
                    <span className="font-semibold text-slate-800">Selected cycle:</span> {activeCycleSummary}
                  </div>

                  {activeCycleNodes.length === 0 ? (
                    <div className="flex-1 flex items-center justify-center text-slate-500 text-sm">
                      No circular flows detected in the recent network.
                    </div>
                  ) : (
                    <>
                      {/* Visual SVG Network */}
                      <svg className="w-full h-full min-h-[300px]" viewBox="0 0 800 400" preserveAspectRatio="xMidYMid meet">
                        <defs>
                          <linearGradient id="node-glow" x1="0%" y1="0%" x2="100%" y2="100%">
                            <stop offset="0%" stopColor="#f8fafc" />
                            <stop offset="100%" stopColor="#dbeafe" />
                          </linearGradient>
                          <marker id="arrow-head" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto" markerUnits="strokeWidth">
                            <path d="M 0 0 L 10 5 L 0 10 z" fill="#4f46e5" />
                          </marker>
                        </defs>
                        <g>
                          {/* Directed links first so they appear beneath nodes */}
                          {activeCycleLinks.map((link, idx) => {
                            const s = activeNodePositions[link.source];
                            const t = activeNodePositions[link.target];
                            if (!s || !t) return null;
                            const strokeWidth = Math.max(1.5, Math.min(7, Math.log10((link.amount || 1) + 1)));
                            const isCycleEdge = activeCycleNodeSet.has(link.source) && activeCycleNodeSet.has(link.target);
                            const reverseIndex = activeCycleLinks.findIndex(
                              (other) => other.source === link.target && other.target === link.source
                            );
                            const isReversePair = reverseIndex >= 0;
                            const path = getLinkPath(s, t, idx, isReversePair);
                            return (
                              <g
                                key={link.id}
                                onMouseEnter={() => setHoveredGraphItem(getLinkHoverDetails(link))}
                                onMouseLeave={() => setHoveredGraphItem(null)}
                              >
                                <path
                                  d={path}
                                  fill="none"
                                  stroke={isCycleEdge ? '#ef4444' : '#4f46e5'}
                                  strokeOpacity={isCycleEdge ? 0.72 : 0.48}
                                  strokeWidth={strokeWidth}
                                  markerEnd="url(#arrow-head)"
                                />
                                <text
                                  x={(s.x + t.x) / 2}
                                  y={(s.y + t.y) / 2 - (isReversePair ? 16 : 8)}
                                  textAnchor="middle"
                                  className="text-[10px] fill-slate-500 font-mono pointer-events-none"
                                >
                                  {link.source} → {link.target}
                                </text>
                              </g>
                            );
                          })}

                          {/* Nodes */}
                          {activeCycleNodes.map((node) => {
                            const p = activeNodePositions[node.id];
                            if (!p) return null;
                            const active = activeCycleNodeSet.has(node.id);
                            return (
                              <g
                                key={node.id}
                                onMouseEnter={() => setHoveredGraphItem(getNodeHoverDetails(node.id))}
                                onMouseLeave={() => setHoveredGraphItem(null)}
                              >
                                <circle
                                  cx={p.x}
                                  cy={p.y}
                                  r={active ? 22 : 16}
                                  fill="url(#node-glow)"
                                  className={active ? 'stroke-red-500 stroke-2 drop-shadow-sm' : 'stroke-blue-400 stroke-2 drop-shadow-sm'}
                                />
                                <text x={p.x} y={p.y + 36} textAnchor="middle" className="text-xs fill-slate-700 font-semibold font-mono pointer-events-none">
                                  {node.label}
                                </text>
                              </g>
                            );
                          })}
                        </g>
                      </svg>
                    </>
                  )}

                  {hoveredGraphItem && (
                    <div className="absolute right-4 top-16 w-72 rounded-xl border border-slate-200 bg-white/95 p-3 shadow-lg backdrop-blur">
                      <div className="text-[11px] font-semibold uppercase tracking-widest text-slate-500">
                        {hoveredGraphItem.type}
                      </div>
                      <div className="mt-1 text-sm font-bold text-slate-900">{hoveredGraphItem.title}</div>
                      <div className="mt-2 space-y-1 text-xs text-slate-600">
                        {hoveredGraphItem.details.map((detail, idx) => (
                          <div key={idx} className="rounded-md bg-slate-50 px-2 py-1 border border-slate-100">
                            {detail}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {graphData.alerts.length > 0 && (
                    <div className="bg-red-50/90 border border-red-200 p-2 rounded-lg text-xs text-red-700 font-mono shadow-sm">
                      {graphData.alerts.map((al, idx) => (
                        <div key={idx}>{al}</div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </main>

      {/* Selected Alert Details Modal */}
      {selectedAlert && (
        <div className="fixed inset-0 bg-black/80 flex items-center justify-center p-4 z-50 overflow-y-auto">
          <div className="bg-white border border-gray-200 w-full max-w-4xl rounded-2xl p-6 space-y-6 max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center border-b border-gray-200 pb-4">
              <div>
                <h3 className="text-xl font-bold text-red-600">Case Investigation: Alert Detail</h3>
                <p className="text-xs text-gray-500 font-mono">Alert UUID: {selectedAlert.id}</p>
              </div>
              <button
                onClick={() => setSelectedAlert(null)}
                className="text-gray-700 hover:text-black font-bold"
              >
                ✕ Close
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {/* Left Column: SHAP Value and parameters */}
              <div className="space-y-4 md:col-span-1">
                <h4 className="font-bold text-sm text-gray-700 uppercase tracking-widest border-b border-gray-200 pb-2">
                  XAI Explanations
                </h4>
                {shapExplanations.length === 0 ? (
                  <div className="text-gray-500 text-xs">No XAI values available.</div>
                ) : (
                  <div className="space-y-3">
                    {shapExplanations.map((exp) => (
                      <div key={exp.id} className="space-y-1">
                        <div className="flex justify-between text-xs font-mono">
                          <span>{exp.featureName}</span>
                          <span className={exp.impactValue >= 0 ? 'text-red-600' : 'text-green-600'}>
                            {exp.impactValue >= 0 ? '+' : ''}
                            {exp.impactValue.toFixed(4)}
                          </span>
                        </div>
                        <div className="w-full bg-gray-100 h-2 rounded overflow-hidden">
                          <div
                            className={`h-full ${exp.impactValue >= 0 ? 'bg-red-600' : 'bg-green-600'}`}
                            style={{ width: `${Math.min(Math.abs(exp.impactValue) * 100, 100)}%` }}
                          ></div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Right Column: SAR Report Draft */}
              <div className="md:col-span-2 space-y-4">
                <h4 className="font-bold text-sm text-gray-700 uppercase tracking-widest border-b border-gray-200 pb-2 flex justify-between">
                  <span>Regulator-Ready SAR Narrative</span>
                  <span className="text-xs text-orange-400 normal-case font-mono">Generated by Gemini</span>
                </h4>
                {loadingSar ? (
                  <div className="text-gray-500 text-center py-20">Drafting report using LLM...</div>
                ) : (
                  <pre className="p-4 bg-gray-50 border border-gray-100 rounded-lg text-xs font-mono text-gray-700 whitespace-pre-wrap max-h-[350px] overflow-y-auto">
                    {sarText || 'No report draft available.'}
                  </pre>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
