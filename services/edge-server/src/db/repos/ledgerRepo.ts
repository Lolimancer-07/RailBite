import type Database from "better-sqlite3";

export interface LedgerSummaryRow {
  captainId: string;
  captainName: string;
  expectedPaise: number;
  collectedPaise: number;
  passengerConfirmedPaise: number;
  mismatch: boolean;
}

export class LedgerRepo {
  constructor(private db: Database.Database) {}

  append(orderId: string, captainId: string, amountPaise: number, ts: string): number {
    const res = this.db
      .prepare(
        "INSERT INTO cash_ledger (order_id, captain_id, amount_paise, ts) VALUES (?, ?, ?, ?)",
      )
      .run(orderId, captainId, amountPaise, ts);
    return Number(res.lastInsertRowid);
  }

  confirmByPassenger(orderId: string): boolean {
    const res = this.db
      .prepare("UPDATE cash_ledger SET passenger_confirmed = 1 WHERE order_id = ?")
      .run(orderId);
    return res.changes > 0;
  }

  getSummary(): LedgerSummaryRow[] {
    const rows = this.db
      .prepare(
        `SELECT 
          s.id as captainId,
          s.name as captainName,
          COALESCE(
            (SELECT SUM(o.total_paise)
             FROM orders o
             JOIN assignments a ON a.order_id = o.id
             WHERE a.captain_id = s.id AND o.status = 'DELIVERED' AND o.payment_mode = 'COD'), 0
          ) as expectedPaise,
          COALESCE(
            (SELECT SUM(l.amount_paise) 
             FROM cash_ledger l 
             WHERE l.captain_id = s.id), 0
          ) as collectedPaise,
          COALESCE(
            (SELECT SUM(l.amount_paise) 
             FROM cash_ledger l 
             WHERE l.captain_id = s.id AND l.passenger_confirmed = 1), 0
          ) as passengerConfirmedPaise
         FROM staff s
         WHERE s.role = 'CAPTAIN'`,
      )
      .all() as any[];

    return rows.map((r) => ({
      captainId: r.captainId,
      captainName: r.captainName,
      expectedPaise: Number(r.expectedPaise),
      collectedPaise: Number(r.collectedPaise),
      passengerConfirmedPaise: Number(r.passengerConfirmedPaise),
      mismatch: Number(r.expectedPaise) !== Number(r.collectedPaise) || Number(r.collectedPaise) !== Number(r.passengerConfirmedPaise),
    }));
  }

  /** Today's stats for a single staff member (UTC day), plus FOT-style passenger feedback rating */
  getStaffStats(staffId: string, staffName: string): {
    staffId: string;
    staffName: string;
    date: string;
    ordersDelivered: number;
    expectedPaise: number;
    collectedPaise: number;
    passengerConfirmedPaise: number;
    avgRating: number | null;
    feedbackCount: number;
  } {
    const todayPrefix = new Date().toISOString().slice(0, 10);

    const row = this.db
      .prepare(
        `SELECT
          COUNT(*) as ordersDelivered,
          COALESCE(SUM(CASE WHEN o.payment_mode = 'COD' THEN o.total_paise ELSE 0 END), 0) as expectedPaise,
          COALESCE(SUM(l.amount_paise), 0) as collectedPaise,
          COALESCE(SUM(CASE WHEN l.passenger_confirmed = 1 THEN l.amount_paise ELSE 0 END), 0) as confirmedPaise
         FROM cash_ledger l
         JOIN orders o ON o.id = l.order_id
         WHERE l.captain_id = ? AND l.ts LIKE ? || '%'`,
      )
      .get(staffId, todayPrefix) as any;

    // Passenger-facing rating: star feedback on orders this picker delivered (all-time)
    const ratingRow = this.db
      .prepare(
        `SELECT COUNT(f.rating) as feedbackCount, AVG(f.rating) as avgRating
         FROM order_feedback f
         JOIN assignments a ON a.order_id = f.order_id
         WHERE a.captain_id = ? AND a.delivered_at IS NOT NULL AND f.rating IS NOT NULL`,
      )
      .get(staffId) as any;

    return {
      staffId,
      staffName,
      date: todayPrefix,
      ordersDelivered: Number(row?.ordersDelivered || 0),
      expectedPaise: Number(row?.expectedPaise || 0),
      collectedPaise: Number(row?.collectedPaise || 0),
      passengerConfirmedPaise: Number(row?.confirmedPaise || 0),
      avgRating: ratingRow?.avgRating != null ? Math.round(Number(ratingRow.avgRating) * 10) / 10 : null,
      feedbackCount: Number(ratingRow?.feedbackCount || 0),
    };
  }
}

