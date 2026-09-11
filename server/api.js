import express from 'express';
import cors from 'cors';
import { db, initDatabase } from './db.js';

initDatabase();

export const apiApp = express();

apiApp.use(cors());
apiApp.use(express.json());

// Helper for snake_case to camelCase conversion
function toCamel(obj) {
  if (!obj || typeof obj !== 'object') return obj;
  if (Array.isArray(obj)) return obj.map(toCamel);
  const newObj = {};
  for (const [key, val] of Object.entries(obj)) {
    const camelKey = key.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase());
    // Convert boolean flags stored as 0/1 in SQLite
    if (['isCorner', 'isGarden', 'isHot', 'isImportant'].includes(camelKey)) {
      newObj[camelKey] = Boolean(val);
    } else {
      newObj[camelKey] = val;
    }
  }
  return newObj;
}

// ---------------------------------------------------------------------------
// 1. AUTHENTICATION ENDPOINTS
// ---------------------------------------------------------------------------
apiApp.post('/api/auth/login', (req, res) => {
  try {
    const { email, password, role } = req.body;
    if (!email || !password) {
      return res.status(400).json({ success: false, error: 'Email and password required' });
    }

    let user;
    if (role) {
      user = db.prepare('SELECT * FROM users WHERE email = ? AND role = ?').get(email, role);
    } else {
      user = db.prepare('SELECT * FROM users WHERE email = ?').get(email);
    }

    if (!user) {
      return res.status(401).json({ success: false, error: 'No account found with this email' });
    }

    if (user.password !== password) {
      return res.status(401).json({ success: false, error: 'Invalid password. Please verify.' });
    }

    const { password: _, ...safeUser } = toCamel(user);
    res.json({ success: true, user: safeUser });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

apiApp.post('/api/auth/signup', (req, res) => {
  try {
    const { name, email, password, role, organization, designation, reraNumber } = req.body;
    if (!name || !email || !password) {
      return res.status(400).json({ success: false, error: 'Name, email, and password required' });
    }

    const existing = db.prepare('SELECT id FROM users WHERE email = ?').get(email);
    if (existing) {
      return res.status(400).json({ success: false, error: 'Account with this email already exists' });
    }

    const id = 'usr_' + Date.now();
    const initials = name
      .split(' ')
      .map(n => n[0])
      .join('')
      .toUpperCase()
      .slice(0, 2);

    db.prepare(`
      INSERT INTO users (id, name, email, password, role, organization, designation, rera_number, avatar_initials)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      id,
      name,
      email,
      password,
      role || 'developer',
      organization || '',
      designation || '',
      reraNumber || '',
      initials
    );

    const newUser = db.prepare('SELECT * FROM users WHERE id = ?').get(id);
    const { password: _, ...safeUser } = toCamel(newUser);
    res.json({ success: true, user: safeUser });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// ---------------------------------------------------------------------------
// 2. PROJECTS ENDPOINTS
// ---------------------------------------------------------------------------
apiApp.get('/api/projects', (req, res) => {
  try {
    const projects = db.prepare('SELECT * FROM projects ORDER BY created_at DESC').all();
    res.json(toCamel(projects));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.post('/api/projects', (req, res) => {
  try {
    const p = req.body;
    const id = 'proj_' + Date.now();
    db.prepare(`
      INSERT INTO projects (
        id, name, project_type, status, locality, city, state_code, address,
        rera_number, total_area_value, total_area_unit, total_area_sqft,
        declared_plot_count, launch_date, expected_completion_date, description,
        grid_rows, grid_cols
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      id,
      p.name,
      p.projectType || 'RESIDENTIAL_PLOT_COLONY',
      p.status || 'ACTIVE',
      p.locality || '',
      p.city || '',
      p.stateCode || 'UP',
      p.address || '',
      p.reraNumber || '',
      p.totalAreaValue || 0,
      p.totalAreaUnit || 'BIGHA',
      p.totalAreaSqft || (p.totalAreaValue || 0) * 27000,
      p.declaredPlotCount || 0,
      p.launchDate || new Date().toISOString().split('T')[0],
      p.expectedCompletionDate || '',
      p.description || '',
      p.gridRows || 4,
      p.gridCols || 6
    );

    const created = db.prepare('SELECT * FROM projects WHERE id = ?').get(id);
    res.json(toCamel(created));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.delete('/api/projects/:id', (req, res) => {
  try {
    db.prepare('DELETE FROM projects WHERE id = ?').run(req.params.id);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// 3. PLOTS ENDPOINTS
// ---------------------------------------------------------------------------
apiApp.get('/api/plots', (req, res) => {
  try {
    const { projectId } = req.query;
    let plots;
    if (projectId) {
      plots = db.prepare('SELECT * FROM plots WHERE project_id = ? ORDER BY grid_row ASC, grid_col ASC').all(projectId);
    } else {
      plots = db.prepare('SELECT * FROM plots ORDER BY grid_row ASC, grid_col ASC').all();
    }
    res.json(toCamel(plots));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.post('/api/plots', (req, res) => {
  try {
    const p = req.body;
    const id = 'plt_' + Date.now() + '_' + Math.floor(Math.random() * 1000);
    db.prepare(`
      INSERT INTO plots (
        id, project_id, plot_number, status, reserved_for, reserved_until,
        size_value, size_unit, size_sqft, facing, price, price_per_sqft,
        is_corner, is_garden, is_hot, grid_row, grid_col, current_sale_id
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      id,
      p.projectId,
      p.plotNumber,
      p.status || 'AVAILABLE',
      p.reservedFor || null,
      p.reservedUntil || null,
      p.sizeValue,
      p.sizeUnit || 'SQ_FT',
      p.sizeSqft || p.sizeValue,
      p.facing || 'E',
      p.price,
      p.pricePerSqft || Math.round(p.price / (p.sizeSqft || p.sizeValue)),
      p.isCorner ? 1 : 0,
      p.isGarden ? 1 : 0,
      p.isHot ? 1 : 0,
      p.gridRow || 0,
      p.gridCol || 0,
      p.currentSaleId || null
    );
    const created = db.prepare('SELECT * FROM plots WHERE id = ?').get(id);
    res.json(toCamel(created));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.post('/api/plots/bulk', (req, res) => {
  try {
    const { plots } = req.body;
    if (!Array.isArray(plots) || plots.length === 0) {
      return res.status(400).json({ error: 'Array of plots required' });
    }

    const insert = db.prepare(`
      INSERT INTO plots (
        id, project_id, plot_number, status, size_value, size_unit, size_sqft,
        facing, price, price_per_sqft, is_corner, is_garden, is_hot, grid_row, grid_col
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const insertMany = db.transaction((plotList) => {
      for (const p of plotList) {
        const id = 'plt_' + Date.now() + '_' + Math.floor(Math.random() * 100000);
        insert.run(
          id,
          p.projectId,
          p.plotNumber,
          p.status || 'AVAILABLE',
          p.sizeValue,
          p.sizeUnit || 'SQ_FT',
          p.sizeSqft || p.sizeValue,
          p.facing || 'E',
          p.price,
          p.pricePerSqft || Math.round(p.price / (p.sizeSqft || p.sizeValue)),
          p.isCorner ? 1 : 0,
          p.isGarden ? 1 : 0,
          p.isHot ? 1 : 0,
          p.gridRow || 0,
          p.gridCol || 0
        );
      }
    });

    insertMany(plots);
    res.json({ success: true, count: plots.length });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.put('/api/plots/:id', (req, res) => {
  try {
    const p = req.body;
    db.prepare(`
      UPDATE plots SET
        plot_number = COALESCE(?, plot_number),
        status = COALESCE(?, status),
        reserved_for = ?,
        reserved_until = ?,
        size_value = COALESCE(?, size_value),
        size_sqft = COALESCE(?, size_sqft),
        facing = COALESCE(?, facing),
        price = COALESCE(?, price),
        price_per_sqft = COALESCE(?, price_per_sqft),
        is_corner = COALESCE(?, is_corner),
        is_garden = COALESCE(?, is_garden),
        is_hot = COALESCE(?, is_hot)
      WHERE id = ?
    `).run(
      p.plotNumber,
      p.status,
      p.reservedFor || null,
      p.reservedUntil || null,
      p.sizeValue,
      p.sizeSqft,
      p.facing,
      p.price,
      p.pricePerSqft,
      p.isCorner !== undefined ? (p.isCorner ? 1 : 0) : null,
      p.isGarden !== undefined ? (p.isGarden ? 1 : 0) : null,
      p.isHot !== undefined ? (p.isHot ? 1 : 0) : null,
      req.params.id
    );
    const updated = db.prepare('SELECT * FROM plots WHERE id = ?').get(req.params.id);
    res.json(toCamel(updated));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// 4. LEADS & INTERACTIONS ENDPOINTS
// ---------------------------------------------------------------------------
apiApp.get('/api/leads', (req, res) => {
  try {
    const leads = db.prepare('SELECT * FROM leads ORDER BY created_at DESC').all();
    res.json(toCamel(leads));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.post('/api/leads', (req, res) => {
  try {
    const l = req.body;
    const id = 'lead_' + Date.now();
    db.prepare(`
      INSERT INTO leads (
        id, full_name, mobile, email, budget_min, budget_max,
        preferred_property_type, source, source_broker_id, status,
        interested_project_id, assigned_to, follow_up_date, is_important,
        remarks, last_interaction_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      id,
      l.fullName,
      l.mobile,
      l.email || null,
      l.budgetMin || 0,
      l.budgetMax || 0,
      l.preferredPropertyType || 'PLOT',
      l.source || 'WALK_IN',
      l.sourceBrokerId || null,
      l.status || 'INTERESTED',
      l.interestedProjectId || null,
      l.assignedTo || 'Rajeshwar Singhania',
      l.followUpDate || null,
      l.isImportant ? 1 : 0,
      l.remarks || '',
      new Date().toISOString()
    );

    const created = db.prepare('SELECT * FROM leads WHERE id = ?').get(id);
    res.json(toCamel(created));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.put('/api/leads/:id', (req, res) => {
  try {
    const l = req.body;
    db.prepare(`
      UPDATE leads SET
        status = COALESCE(?, status),
        follow_up_date = COALESCE(?, follow_up_date),
        remarks = COALESCE(?, remarks),
        assigned_to = COALESCE(?, assigned_to),
        is_important = COALESCE(?, is_important)
      WHERE id = ?
    `).run(
      l.status,
      l.followUpDate,
      l.remarks,
      l.assignedTo,
      l.isImportant !== undefined ? (l.isImportant ? 1 : 0) : null,
      req.params.id
    );
    const updated = db.prepare('SELECT * FROM leads WHERE id = ?').get(req.params.id);
    res.json(toCamel(updated));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.get('/api/leads/:id/interactions', (req, res) => {
  try {
    const interactions = db.prepare('SELECT * FROM interactions WHERE customer_id = ? ORDER BY occurred_on DESC').all(req.params.id);
    res.json(toCamel(interactions));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.post('/api/leads/:id/interactions', (req, res) => {
  try {
    const i = req.body;
    const id = 'int_' + Date.now();
    const customerId = req.params.id;

    db.prepare(`
      INSERT INTO interactions (id, customer_id, occurred_on, type, remarks, result, conducted_by)
      VALUES (?, ?, ?, ?, ?, ?, ?)
    `).run(
      id,
      customerId,
      i.occurredOn || new Date().toISOString().split('T')[0],
      i.type || 'CALL',
      i.remarks || '',
      i.result || 'POSITIVE',
      i.conductedBy || 'Executive'
    );

    // Update lead's last_interaction_at
    db.prepare('UPDATE leads SET last_interaction_at = ? WHERE id = ?').run(new Date().toISOString(), customerId);

    const created = db.prepare('SELECT * FROM interactions WHERE id = ?').get(id);
    res.json(toCamel(created));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// 5. SALES, BOOKINGS & ALLOTMENTS ENDPOINTS
// ---------------------------------------------------------------------------
apiApp.get('/api/sales', (req, res) => {
  try {
    const sales = db.prepare('SELECT * FROM plot_sales ORDER BY created_at DESC').all();
    res.json(toCamel(sales));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.post('/api/sales', (req, res) => {
  try {
    const b = req.body;
    const saleId = 'sale_' + Date.now();
    const allotmentNo = `SHR/${new Date().getFullYear()}/${b.plotNumber.replace(/[^0-9]/g, '') || Math.floor(100 + Math.random() * 900)}`;

    const executeBooking = db.transaction(() => {
      // 1. Insert into plot_sales
      db.prepare(`
        INSERT INTO plot_sales (
          id, plot_id, project_id, buyer_name, buyer_mobile, buyer_email,
          buyer_pan, purchase_date, deal_value, token_amount, payment_type,
          broker_id, status, total_paid, balance_due, allotment_letter_no
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).run(
        saleId,
        b.plotId,
        b.projectId,
        b.buyerName,
        b.buyerMobile,
        b.buyerEmail || null,
        b.buyerGovIdLast4 || null,
        b.purchaseDate || new Date().toISOString().split('T')[0],
        b.dealValue,
        b.tokenAmount || 0,
        b.paymentType || 'INSTALMENT',
        b.brokerId || null,
        'ACTIVE',
        b.tokenAmount || 0,
        b.dealValue - (b.tokenAmount || 0),
        allotmentNo
      );

      // 2. Update plot status to SOLD
      db.prepare('UPDATE plots SET status = ?, current_sale_id = ? WHERE id = ?').run('SOLD', saleId, b.plotId);

      // 3. Generate standard 4-milestone payment schedules
      const milestones = [
        { seq: 1, label: 'Booking Token (10%)', pct: 0.10, dueDays: 0, status: 'PAID', alloc: b.tokenAmount || 0 },
        { seq: 2, label: 'Boundary & Demarcation (30%)', pct: 0.30, dueDays: 60, status: 'PENDING', alloc: 0 },
        { seq: 3, label: 'Internal Roads & Electrification (30%)', pct: 0.30, dueDays: 120, status: 'PENDING', alloc: 0 },
        { seq: 4, label: 'Registry Deed Execution (30%)', pct: 0.30, dueDays: 180, status: 'PENDING', alloc: 0 },
      ];

      for (const m of milestones) {
        const schId = `sch_${saleId}_${m.seq}`;
        const dueDate = new Date();
        dueDate.setDate(dueDate.getDate() + m.dueDays);
        const expAmount = Math.round(b.dealValue * m.pct);

        db.prepare(`
          INSERT INTO payment_schedules (id, plot_sale_id, sequence_no, label, expected_amount, due_date, status, amount_allocated)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `).run(
          schId,
          saleId,
          m.seq,
          m.label,
          expAmount,
          dueDate.toISOString().split('T')[0],
          m.status,
          m.alloc
        );
      }

      // 4. Record initial booking payment receipt
      if (b.tokenAmount && b.tokenAmount > 0) {
        const payId = 'pay_' + Date.now();
        const rcpNo = `RCP/${new Date().getFullYear()}/${Math.floor(1000 + Math.random() * 9000)}`;
        db.prepare(`
          INSERT INTO payment_records (id, plot_sale_id, project_id, receipt_no, amount, paid_on, mode, reference, received_by, remarks)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `).run(
          payId,
          saleId,
          b.projectId,
          rcpNo,
          b.tokenAmount,
          b.purchaseDate || new Date().toISOString().split('T')[0],
          'BANK_TRANSFER',
          'TOKEN-ADVANCE-' + Date.now().toString().slice(-6),
          'Escrow Accounts Desk',
          'Advance booking token credited to RERA Escrow account'
        );
      }

      // 5. Generate Broker Commission Voucher if broker tagged
      if (b.brokerId) {
        const broker = db.prepare('SELECT * FROM brokers WHERE id = ?').get(b.brokerId);
        if (broker) {
          const rate = broker.commission_rate || 2.0;
          const commAmt = Math.round((b.dealValue * rate) / 100);
          const tds = Math.round((commAmt * 5) / 100);
          const net = commAmt - tds;
          const vchNo = `VCH/${new Date().getFullYear()}/${Math.floor(100 + Math.random() * 900)}`;

          db.prepare(`
            INSERT INTO commission_vouchers (
              id, voucher_no, broker_id, plot_sale_id, plot_number, project_name,
              deal_value, commission_amount, tds_deduction, net_payable, deal_date, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          `).run(
            'vch_' + Date.now(),
            vchNo,
            broker.id,
            saleId,
            b.plotNumber,
            b.projectName || 'Township Project',
            b.dealValue,
            commAmt,
            tds,
            net,
            b.purchaseDate || new Date().toISOString().split('T')[0],
            'APPROVED'
          );

          // Update broker metrics
          db.prepare(`
            UPDATE brokers SET
              deals_closed_count = deals_closed_count + 1,
              total_commission_earned = total_commission_earned + ?
            WHERE id = ?
          `).run(commAmt, broker.id);
        }
      }
    });

    executeBooking();
    const createdSale = db.prepare('SELECT * FROM plot_sales WHERE id = ?').get(saleId);
    res.json(toCamel(createdSale));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// 6. FINANCIALS & PAYMENTS ENDPOINTS
// ---------------------------------------------------------------------------
apiApp.get('/api/payments', (req, res) => {
  try {
    const payments = db.prepare('SELECT * FROM payment_records ORDER BY paid_on DESC').all();
    res.json(toCamel(payments));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.post('/api/payments', (req, res) => {
  try {
    const p = req.body;
    const payId = 'pay_' + Date.now();
    const rcpNo = `RCP/${new Date().getFullYear()}/${Math.floor(1000 + Math.random() * 9000)}`;

    const recordPayTransaction = db.transaction(() => {
      // 1. Insert into payment_records
      db.prepare(`
        INSERT INTO payment_records (id, plot_sale_id, project_id, receipt_no, amount, paid_on, mode, reference, received_by, remarks)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `).run(
        payId,
        p.plotSaleId,
        p.projectId || '',
        rcpNo,
        p.amount,
        p.paidOn || new Date().toISOString().split('T')[0],
        p.mode || 'BANK_TRANSFER',
        p.reference || '',
        p.receivedBy || 'Accounts Officer',
        p.remarks || ''
      );

      // 2. Update plot_sales total_paid & balance_due
      db.prepare(`
        UPDATE plot_sales SET
          total_paid = total_paid + ?,
          balance_due = MAX(0, balance_due - ?)
        WHERE id = ?
      `).run(p.amount, p.amount, p.plotSaleId);

      // 3. Auto-allocate payment to milestone schedules
      let unallocated = p.amount;
      const schedules = db.prepare('SELECT * FROM payment_schedules WHERE plot_sale_id = ? ORDER BY sequence_no ASC').all(p.plotSaleId);

      for (const sch of schedules) {
        if (unallocated <= 0) break;
        const remainingOnSchedule = sch.expected_amount - sch.amount_allocated;
        if (remainingOnSchedule > 0) {
          const allocation = Math.min(unallocated, remainingOnSchedule);
          const newAlloc = sch.amount_allocated + allocation;
          const newStatus = newAlloc >= sch.expected_amount ? 'PAID' : 'PARTIALLY_PAID';

          db.prepare('UPDATE payment_schedules SET amount_allocated = ?, status = ? WHERE id = ?').run(newAlloc, newStatus, sch.id);
          unallocated -= allocation;
        }
      }
    });

    recordPayTransaction();
    const created = db.prepare('SELECT * FROM payment_records WHERE id = ?').get(payId);
    res.json(toCamel(created));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.get('/api/schedules', (req, res) => {
  try {
    const schedules = db.prepare('SELECT * FROM payment_schedules ORDER BY sequence_no ASC').all();
    res.json(toCamel(schedules));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// 7. BROKERS & VOUCHERS ENDPOINTS
// ---------------------------------------------------------------------------
apiApp.get('/api/brokers', (req, res) => {
  try {
    const brokers = db.prepare('SELECT * FROM brokers ORDER BY deals_closed_count DESC').all();
    res.json(toCamel(brokers));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.post('/api/brokers', (req, res) => {
  try {
    const b = req.body;
    const id = 'brk_' + Date.now();
    db.prepare(`
      INSERT INTO brokers (
        id, full_name, mobile, email, firm_name, city_area,
        rera_number, commission_type, commission_rate, tier, status
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      id,
      b.fullName,
      b.mobile,
      b.email || null,
      b.firmName,
      b.cityArea || '',
      b.reraNumber || '',
      b.commissionType || 'PERCENTAGE',
      b.commissionRate || 2.0,
      b.tier || 'Gold',
      'ACTIVE'
    );

    const created = db.prepare('SELECT * FROM brokers WHERE id = ?').get(id);
    res.json(toCamel(created));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.put('/api/brokers/:id', (req, res) => {
  try {
    const b = req.body;
    db.prepare(`
      UPDATE brokers SET
        tier = COALESCE(?, tier),
        commission_rate = COALESCE(?, commission_rate),
        status = COALESCE(?, status)
      WHERE id = ?
    `).run(b.tier, b.commissionRate, b.status, req.params.id);

    const updated = db.prepare('SELECT * FROM brokers WHERE id = ?').get(req.params.id);
    res.json(toCamel(updated));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.get('/api/vouchers', (req, res) => {
  try {
    const vouchers = db.prepare('SELECT * FROM commission_vouchers ORDER BY created_at DESC').all();
    res.json(toCamel(vouchers));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.put('/api/vouchers/:id/pay', (req, res) => {
  try {
    const { paymentRef } = req.body;
    const vch = db.prepare('SELECT * FROM commission_vouchers WHERE id = ?').get(req.params.id);
    if (!vch) return res.status(404).json({ error: 'Voucher not found' });

    db.transaction(() => {
      db.prepare('UPDATE commission_vouchers SET status = ?, payment_ref = ? WHERE id = ?').run('PAID', paymentRef || 'NEFT-SETTLED', req.params.id);
      db.prepare('UPDATE brokers SET total_commission_paid = total_commission_paid + ? WHERE id = ?').run(vch.net_payable, vch.broker_id);
    })();

    const updated = db.prepare('SELECT * FROM commission_vouchers WHERE id = ?').get(req.params.id);
    res.json(toCamel(updated));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ---------------------------------------------------------------------------
// 8. CALENDAR ENDPOINTS
// ---------------------------------------------------------------------------
apiApp.get('/api/calendar', (req, res) => {
  try {
    const events = db.prepare('SELECT * FROM calendar_events ORDER BY event_date ASC').all();
    res.json(toCamel(events));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.post('/api/calendar', (req, res) => {
  try {
    const ev = req.body;
    const id = 'evt_' + Date.now();
    db.prepare(`
      INSERT INTO calendar_events (id, title, event_date, event_time, event_type, entity_name, contact_number, assigned_to, status)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      id,
      ev.title,
      ev.eventDate,
      ev.eventTime || '11:00 AM',
      ev.eventType || 'SITE_VISIT',
      ev.entityName || '',
      ev.contactNumber || '',
      ev.assignedTo || 'Executive',
      ev.status || 'SCHEDULED'
    );

    const created = db.prepare('SELECT * FROM calendar_events WHERE id = ?').get(id);
    res.json(toCamel(created));
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

apiApp.delete('/api/calendar/:id', (req, res) => {
  try {
    db.prepare('DELETE FROM calendar_events WHERE id = ?').run(req.params.id);
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});
