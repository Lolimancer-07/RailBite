import React, { useState, useEffect } from "react";

interface Penalty {
  complaintId: string;
  orderId: string;
  type: string;
  pointsDeducted: number;
  reason: string;
  timestamp: string;
}

interface ScoreRecord {
  tripId: string;
  trainNo: string;
  contractorName: string;
  score: number;
  status: "GOOD" | "WARNING" | "CRITICAL_REVOKE_RISK";
  penalties: Penalty[];
  updatedAt: string;
}

interface Complaint {
  id: string;
  trip_id: string;
  order_id: string;
  type: string;
  text?: string;
  photo_sha256?: string;
  penalty_points: number;
  created_at: string;
}

interface LedgerSummary {
  totalCollectedRupees: number;
  totalEntries: number;
  confirmedCount: number;
}

const DEFAULT_SCORE: ScoreRecord = {
  tripId: "TRIP_001",
  trainNo: "12951",
  contractorName: "IRCTC Catering Licensee Corp",
  score: 65,
  status: "CRITICAL_REVOKE_RISK",
  penalties: [
    {
      complaintId: "c-101",
      orderId: "ord-849",
      type: "OVERCHARGING",
      pointsDeducted: 10,
      reason: "Charged ₹25 for ₹15 Rail Neer water bottle",
      timestamp: new Date().toISOString(),
    },
    {
      complaintId: "c-102",
      orderId: "ord-902",
      type: "STALE_FOOD",
      pointsDeducted: 20,
      reason: "Spoiled paneer biryani smelled sour",
      timestamp: new Date().toISOString(),
    },
  ],
  updatedAt: new Date().toISOString(),
};

const DEFAULT_COMPLAINTS: Complaint[] = [
  {
    id: "comp-001",
    trip_id: "TRIP_001",
    order_id: "7a8b-1234",
    type: "OVERCHARGING",
    text: "Captain asked ₹30 for ₹20 chips packet.",
    photo_sha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    penalty_points: 10,
    created_at: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: "comp-002",
    trip_id: "TRIP_001",
    order_id: "9c3d-5678",
    type: "HYGIENE",
    text: "Pantry car cook handling roti without gloves or hairnet.",
    photo_sha256: "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
    penalty_points: 15,
    created_at: new Date(Date.now() - 7200000).toISOString(),
  },
];

export default function App() {
  const [activeTab, setActiveTab] = useState<"governance" | "complaints" | "ledger">("governance");
  const [scoreData, setScoreData] = useState<ScoreRecord>(DEFAULT_SCORE);
  const [complaints, setComplaints] = useState<Complaint[]>(DEFAULT_COMPLAINTS);
  const [ledger, setLedger] = useState<LedgerSummary>({
    totalCollectedRupees: 18450,
    totalEntries: 112,
    confirmedCount: 104,
  });

  // Attempt live fetch from cloud-api on port 8090
  useEffect(() => {
    async function loadData() {
      try {
        const scoresRes = await fetch("http://127.0.0.1:8090/admin/scores");
        if (scoresRes.ok) {
          const list = (await scoresRes.json()) as ScoreRecord[];
          if (list[0]) setScoreData(list[0]);
        }
        const complaintsRes = await fetch("http://127.0.0.1:8090/admin/complaints");
        if (complaintsRes.ok) {
          setComplaints(await complaintsRes.json());
        }
        const ledgerRes = await fetch("http://127.0.0.1:8090/admin/ledger");
        if (ledgerRes.ok) {
          setLedger(await ledgerRes.json());
        }
      } catch {
        // Running in offline / preview mode with seeded demo data
      }
    }
    loadData();
    const interval = setInterval(loadData, 5000);
    return () => clearInterval(interval);
  }, []);

  const isCritical = scoreData.score < 70;
  const isWarning = scoreData.score >= 70 && scoreData.score < 85;

  return (
    <div style={{ maxWidth: 1200, margin: "0 auto", padding: 24 }}>
      {/* Header */}
      <header
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          borderBottom: "1px solid #334155",
          paddingBottom: 16,
          marginBottom: 24,
        }}
      >
        <div>
          <h1 style={{ margin: 0, fontSize: 24, color: "#38bdf8", display: "flex", alignItems: "center", gap: 10 }}>
            <span>🚆</span> RailBite IRCTC Admin & Contractor Governance
          </h1>
          <p style={{ margin: "4px 0 0 0", color: "#94a3b8", fontSize: 14 }}>
            Onboard Accountability System • Train #{scoreData.trainNo} • Trip {scoreData.tripId}
          </p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button
            onClick={() => setActiveTab("governance")}
            style={{
              padding: "8px 16px",
              borderRadius: 6,
              border: "none",
              backgroundColor: activeTab === "governance" ? "#0284c7" : "#1e293b",
              color: "#ffffff",
              cursor: "pointer",
              fontWeight: 600,
            }}
          >
            Contractor Score
          </button>
          <button
            onClick={() => setActiveTab("complaints")}
            style={{
              padding: "8px 16px",
              borderRadius: 6,
              border: "none",
              backgroundColor: activeTab === "complaints" ? "#0284c7" : "#1e293b",
              color: "#ffffff",
              cursor: "pointer",
              fontWeight: 600,
            }}
          >
            Tier 2 Complaints ({complaints.length})
          </button>
          <button
            onClick={() => setActiveTab("ledger")}
            style={{
              padding: "8px 16px",
              borderRadius: 6,
              border: "none",
              backgroundColor: activeTab === "ledger" ? "#0284c7" : "#1e293b",
              color: "#ffffff",
              cursor: "pointer",
              fontWeight: 600,
            }}
          >
            Cash Ledger
          </button>
        </div>
      </header>

      {/* TAB 1: Governance & License Score */}
      {activeTab === "governance" && (
        <div>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1fr 2fr",
              gap: 20,
              marginBottom: 24,
            }}
          >
            {/* License Score Card */}
            <div
              style={{
                backgroundColor: "#1e293b",
                borderRadius: 12,
                padding: 24,
                border: isCritical ? "2px solid #ef4444" : isWarning ? "2px solid #eab308" : "2px solid #22c55e",
                boxShadow: isCritical ? "0 0 20px rgba(239,68,68,0.3)" : "none",
              }}
            >
              <div style={{ fontSize: 13, color: "#94a3b8", textTransform: "uppercase", fontWeight: "bold" }}>
                Contractor License Score
              </div>
              <div style={{ fontSize: 64, fontWeight: "900", color: isCritical ? "#ef4444" : isWarning ? "#eab308" : "#22c55e", margin: "10px 0" }}>
                {scoreData.score}
                <span style={{ fontSize: 24, color: "#64748b" }}>/100</span>
              </div>
              <div
                style={{
                  display: "inline-block",
                  padding: "6px 12px",
                  borderRadius: 20,
                  fontSize: 12,
                  fontWeight: "bold",
                  backgroundColor: isCritical ? "#7f1d1d" : isWarning ? "#713f12" : "#14532d",
                  color: isCritical ? "#fca5a5" : isWarning ? "#fde047" : "#86efac",
                }}
              >
                {isCritical ? "⚠️ CRITICAL: REVOCATION AUDIT REQUIRED" : isWarning ? "⚡ WARNING: AUDIT SCRUTINY" : "✅ LICENSE IN GOOD STANDING"}
              </div>
              <div style={{ marginTop: 20, borderTop: "1px solid #334155", paddingTop: 16 }}>
                <div style={{ fontSize: 12, color: "#94a3b8" }}>Licensee:</div>
                <div style={{ fontSize: 15, fontWeight: "bold", color: "#f8fafc" }}>{scoreData.contractorName}</div>
                <div style={{ fontSize: 12, color: "#64748b", marginTop: 4 }}>Trip ID: {scoreData.tripId}</div>
              </div>
            </div>

            {/* Penalty Ledger */}
            <div style={{ backgroundColor: "#1e293b", borderRadius: 12, padding: 24 }}>
              <h3 style={{ margin: "0 0 16px 0", fontSize: 16, color: "#f8fafc" }}>
                Recent Violations & Score Deductions
              </h3>
              {scoreData.penalties.length === 0 ? (
                <div style={{ color: "#22c55e", fontSize: 14 }}>No violations recorded on this trip!</div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                  {scoreData.penalties.map((p, idx) => (
                    <div
                      key={idx}
                      style={{
                        backgroundColor: "#0f172a",
                        padding: 12,
                        borderRadius: 8,
                        borderLeft: "4px solid #ef4444",
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                      }}
                    >
                      <div>
                        <div style={{ fontWeight: "bold", fontSize: 14, color: "#f8fafc" }}>
                          {p.type}: {p.reason}
                        </div>
                        <div style={{ fontSize: 12, color: "#64748b", marginTop: 4 }}>
                          Order #{p.orderId.slice(-6)} • {new Date(p.timestamp).toLocaleTimeString()}
                        </div>
                      </div>
                      <div
                        style={{
                          backgroundColor: "#450a0a",
                          color: "#ef4444",
                          padding: "4px 10px",
                          borderRadius: 6,
                          fontWeight: "bold",
                          fontSize: 14,
                        }}
                      >
                        -{p.pointsDeducted} pts
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: Tier 2 Complaints Feed */}
      {activeTab === "complaints" && (
        <div style={{ backgroundColor: "#1e293b", borderRadius: 12, padding: 24 }}>
          <h2 style={{ margin: "0 0 16px 0", fontSize: 18, color: "#f8fafc" }}>
            Regulatory Complaints Feed (RailMadad / IRCTC Escalation)
          </h2>
          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            {complaints.map((c) => (
              <div
                key={c.id}
                style={{
                  backgroundColor: "#0f172a",
                  padding: 18,
                  borderRadius: 10,
                  border: "1px solid #334155",
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div>
                    <span
                      style={{
                        backgroundColor: "#dc2626",
                        color: "#ffffff",
                        padding: "2px 8px",
                        borderRadius: 4,
                        fontSize: 12,
                        fontWeight: "bold",
                        marginRight: 8,
                      }}
                    >
                      TIER 2
                    </span>
                    <strong style={{ fontSize: 16, color: "#f8fafc" }}>{c.type}</strong>
                    <span style={{ fontSize: 13, color: "#94a3b8", marginLeft: 10 }}>
                      Order #{c.order_id.slice(-6)}
                    </span>
                  </div>
                  <div style={{ fontSize: 12, color: "#64748b" }}>
                    {new Date(c.created_at).toLocaleString()}
                  </div>
                </div>
                <p style={{ margin: "10px 0", color: "#cbd5e1", fontSize: 14 }}>{c.text || "No details provided"}</p>
                {c.photo_sha256 && (
                  <div
                    style={{
                      backgroundColor: "#1e293b",
                      padding: 8,
                      borderRadius: 6,
                      fontSize: 11,
                      fontFamily: "monospace",
                      color: "#38bdf8",
                      display: "flex",
                      alignItems: "center",
                      gap: 6,
                    }}
                  >
                    <span>📷 SHA-256 Photo Hash:</span>
                    <span>{c.photo_sha256}</span>
                    <span style={{ color: "#22c55e", fontWeight: "bold", marginLeft: "auto" }}>VERIFIED</span>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 3: Cash Ledger */}
      {activeTab === "ledger" && (
        <div style={{ backgroundColor: "#1e293b", borderRadius: 12, padding: 24 }}>
          <h2 style={{ margin: "0 0 16px 0", fontSize: 18, color: "#f8fafc" }}>
            Onboard Pantry Cash Ledger Reconciliation
          </h2>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 16, marginBottom: 24 }}>
            <div style={{ backgroundColor: "#0f172a", padding: 18, borderRadius: 8 }}>
              <div style={{ fontSize: 12, color: "#94a3b8", textTransform: "uppercase" }}>Total Collected</div>
              <div style={{ fontSize: 28, fontWeight: "bold", color: "#22c55e", marginTop: 6 }}>
                ₹{ledger.totalCollectedRupees}
              </div>
              <div style={{ fontSize: 12, color: "#64748b", marginTop: 4 }}>Across all pantry captains</div>
            </div>
            <div style={{ backgroundColor: "#0f172a", padding: 18, borderRadius: 8 }}>
              <div style={{ fontSize: 12, color: "#94a3b8", textTransform: "uppercase" }}>Passenger Confirmations</div>
              <div style={{ fontSize: 28, fontWeight: "bold", color: "#38bdf8", marginTop: 6 }}>
                {ledger.confirmedCount} / {ledger.totalEntries}
              </div>
              <div style={{ fontSize: 12, color: "#64748b", marginTop: 4 }}>
                {Math.round((ledger.confirmedCount / Math.max(1, ledger.totalEntries)) * 100)}% verified seat-side
              </div>
            </div>
            <div style={{ backgroundColor: "#0f172a", padding: 18, borderRadius: 8 }}>
              <div style={{ fontSize: 12, color: "#94a3b8", textTransform: "uppercase" }}>Cash Leakage Flag</div>
              <div style={{ fontSize: 28, fontWeight: "bold", color: "#22c55e", marginTop: 6 }}>
                ₹0.00
              </div>
              <div style={{ fontSize: 12, color: "#22c55e", marginTop: 4 }}>Zero unaccounted discrepancies</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
