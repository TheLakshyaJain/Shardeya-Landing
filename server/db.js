import Database from 'better-sqlite3';
import path from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const dbDir = path.join(__dirname, 'data');
if (!fs.existsSync(dbDir)) {
  fs.mkdirSync(dbDir, { recursive: true });
}

const dbPath = path.join(dbDir, 'shardeya.sqlite');
export const db = new Database(dbPath);

// Enable WAL mode and foreign keys for high-performance concurrent reads/writes
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

export function initDatabase() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      email TEXT UNIQUE NOT NULL,
      password TEXT NOT NULL,
      role TEXT NOT NULL,
      organization TEXT,
      designation TEXT,
      rera_number TEXT,
      avatar_initials TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS projects (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      project_type TEXT NOT NULL,
      status TEXT NOT NULL,
      locality TEXT,
      city TEXT,
      state_code TEXT,
      address TEXT,
      rera_number TEXT,
      total_area_value REAL DEFAULT 0,
      total_area_unit TEXT DEFAULT 'BIGHA',
      total_area_sqft REAL DEFAULT 0,
      declared_plot_count INTEGER DEFAULT 0,
      launch_date TEXT,
      expected_completion_date TEXT,
      description TEXT,
      grid_rows INTEGER DEFAULT 4,
      grid_cols INTEGER DEFAULT 6,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS plots (
      id TEXT PRIMARY KEY,
      project_id TEXT NOT NULL,
      plot_number TEXT NOT NULL,
      status TEXT NOT NULL,
      reserved_for TEXT,
      reserved_until TEXT,
      size_value REAL NOT NULL,
      size_unit TEXT NOT NULL,
      size_sqft REAL NOT NULL,
      facing TEXT NOT NULL,
      price REAL NOT NULL,
      price_per_sqft REAL NOT NULL,
      is_corner INTEGER DEFAULT 0,
      is_garden INTEGER DEFAULT 0,
      is_hot INTEGER DEFAULT 0,
      grid_row INTEGER DEFAULT 0,
      grid_col INTEGER DEFAULT 0,
      current_sale_id TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS plot_sales (
      id TEXT PRIMARY KEY,
      plot_id TEXT NOT NULL,
      project_id TEXT NOT NULL,
      buyer_name TEXT NOT NULL,
      buyer_mobile TEXT NOT NULL,
      buyer_email TEXT,
      buyer_pan TEXT,
      purchase_date TEXT NOT NULL,
      deal_value REAL NOT NULL,
      token_amount REAL DEFAULT 0,
      payment_type TEXT NOT NULL,
      broker_id TEXT,
      status TEXT NOT NULL,
      total_paid REAL DEFAULT 0,
      balance_due REAL DEFAULT 0,
      allotment_letter_no TEXT NOT NULL,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (plot_id) REFERENCES plots(id),
      FOREIGN KEY (project_id) REFERENCES projects(id)
    );

    CREATE TABLE IF NOT EXISTS payment_schedules (
      id TEXT PRIMARY KEY,
      plot_sale_id TEXT NOT NULL,
      sequence_no INTEGER NOT NULL,
      label TEXT NOT NULL,
      expected_amount REAL NOT NULL,
      due_date TEXT NOT NULL,
      status TEXT NOT NULL,
      amount_allocated REAL DEFAULT 0,
      days_overdue INTEGER DEFAULT 0,
      FOREIGN KEY (plot_sale_id) REFERENCES plot_sales(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS payment_records (
      id TEXT PRIMARY KEY,
      plot_sale_id TEXT NOT NULL,
      project_id TEXT NOT NULL,
      receipt_no TEXT NOT NULL,
      amount REAL NOT NULL,
      paid_on TEXT NOT NULL,
      mode TEXT NOT NULL,
      reference TEXT,
      received_by TEXT,
      remarks TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (plot_sale_id) REFERENCES plot_sales(id)
    );

    CREATE TABLE IF NOT EXISTS leads (
      id TEXT PRIMARY KEY,
      full_name TEXT NOT NULL,
      mobile TEXT NOT NULL,
      email TEXT,
      budget_min REAL DEFAULT 0,
      budget_max REAL DEFAULT 0,
      preferred_property_type TEXT DEFAULT 'PLOT',
      source TEXT NOT NULL,
      source_broker_id TEXT,
      status TEXT NOT NULL,
      interested_project_id TEXT,
      assigned_to TEXT,
      follow_up_date TEXT,
      is_important INTEGER DEFAULT 0,
      remarks TEXT,
      last_interaction_at TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS interactions (
      id TEXT PRIMARY KEY,
      customer_id TEXT NOT NULL,
      occurred_on TEXT NOT NULL,
      type TEXT NOT NULL,
      remarks TEXT,
      result TEXT,
      conducted_by TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (customer_id) REFERENCES leads(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS brokers (
      id TEXT PRIMARY KEY,
      full_name TEXT NOT NULL,
      mobile TEXT NOT NULL,
      email TEXT,
      firm_name TEXT NOT NULL,
      city_area TEXT,
      rera_number TEXT,
      commission_type TEXT DEFAULT 'PERCENTAGE',
      commission_rate REAL DEFAULT 2.0,
      tier TEXT DEFAULT 'Gold',
      deals_closed_count INTEGER DEFAULT 0,
      total_commission_earned REAL DEFAULT 0,
      total_commission_paid REAL DEFAULT 0,
      status TEXT DEFAULT 'ACTIVE',
      parent_broker_id TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS commission_vouchers (
      id TEXT PRIMARY KEY,
      voucher_no TEXT NOT NULL,
      broker_id TEXT NOT NULL,
      plot_sale_id TEXT,
      plot_number TEXT,
      project_name TEXT,
      deal_value REAL NOT NULL,
      commission_amount REAL NOT NULL,
      tds_deduction REAL NOT NULL,
      net_payable REAL NOT NULL,
      deal_date TEXT NOT NULL,
      status TEXT NOT NULL,
      payment_ref TEXT,
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (broker_id) REFERENCES brokers(id)
    );

    CREATE TABLE IF NOT EXISTS calendar_events (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      event_date TEXT NOT NULL,
      event_time TEXT,
      event_type TEXT NOT NULL,
      entity_name TEXT,
      contact_number TEXT,
      assigned_to TEXT,
      status TEXT DEFAULT 'SCHEDULED',
      created_at DATETIME DEFAULT CURRENT_TIMESTAMP
    );
  `);

  // Ensure default developer user exists in database
  const developerUser = db.prepare('SELECT id FROM users WHERE email = ?').get('developer@shardeya.com');
  if (!developerUser) {
    db.prepare(`
      INSERT INTO users (id, name, email, password, role, organization, designation, rera_number, avatar_initials)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      'usr_dev_01',
      'Rajeshwar Singhania',
      'developer@shardeya.com',
      'Shardeya@2026',
      'developer',
      'Shardeya Group Pvt. Ltd.',
      'Managing Director & Promoter',
      'UPRERA/PRJ992182/2026',
      'RS'
    );
  }

  // Ensure default channel partner user exists in database
  const brokerUser = db.prepare('SELECT id FROM users WHERE email = ?').get('partner@apexrealty.com');
  if (!brokerUser) {
    db.prepare(`
      INSERT INTO users (id, name, email, password, role, organization, designation, rera_number, avatar_initials)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      'usr_brk_01',
      'Vikram Malhotra',
      'partner@apexrealty.com',
      'Partner@2026',
      'broker',
      'Diamond Realty Syndicate',
      'Managing Partner',
      'UPRERA/A/2025/8812',
      'VM'
    );
  }

  // Initialize clean project if empty
  const projectCount = db.prepare('SELECT COUNT(*) as count FROM projects').get().count;
  if (projectCount === 0) {
    const projId = 'proj_shardeya_01';
    db.prepare(`
      INSERT INTO projects (
        id, name, project_type, status, locality, city, state_code, address,
        rera_number, total_area_value, total_area_unit, total_area_sqft,
        declared_plot_count, launch_date, expected_completion_date, description,
        grid_rows, grid_cols
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      projId,
      'Shardeya Greens Phase 1 & 2',
      'RESIDENTIAL_PLOT_COLONY',
      'ACTIVE',
      'Haridwar - Dehradun Corridor',
      'Saharanpur',
      'UP',
      'Sector 14-A, Shardeya Knowledge City, Saharanpur Bypass',
      'UPRERA/PRJ992182/2026',
      42,
      'BIGHA',
      1134000,
      148,
      '2026-01-15',
      '2028-12-31',
      'Masterplanned residential plotting colony by Shardeya Group Pvt. Ltd. with 60ft wide arterial boulevards and RERA compliant infrastructure.',
      4,
      6
    );

    // Populate clean cadastral layout grid with 24 available plots
    const insertPlot = db.prepare(`
      INSERT INTO plots (
        id, project_id, plot_number, status, size_value, size_unit, size_sqft,
        facing, price, price_per_sqft, is_corner, is_garden, is_hot, grid_row, grid_col
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const facings = ['NE', 'E', 'E', 'N', 'N', 'N', 'E', 'E', 'E', 'W', 'W', 'NW', 'E', 'E', 'W', 'W', 'W', 'W', 'SE', 'S', 'S', 'S', 'S', 'SW'];
    const sizes = [2150, 1800, 2400, 2400, 1800, 1800, 2000, 2000, 2700, 2700, 1800, 2150, 1800, 1800, 2400, 2400, 1800, 2000, 2150, 1800, 1800, 2400, 1800, 2150];

    db.transaction(() => {
      let plotIdx = 101;
      for (let r = 0; r < 4; r++) {
        for (let c = 0; c < 6; c++) {
          const idx = r * 6 + c;
          const sqft = sizes[idx] || 1800;
          const isCorner = (r === 0 && (c === 0 || c === 5)) || (r === 3 && (c === 0 || c === 5));
          const isGarden = (r === 0 && c === 2) || (r === 1 && c === 2);
          const price = sqft * 2200;
          insertPlot.run(
            `plt_${plotIdx}`,
            projId,
            `Plot #${plotIdx}`,
            'AVAILABLE',
            sqft,
            'SQ_FT',
            sqft,
            facings[idx] || 'E',
            price,
            2200,
            isCorner ? 1 : 0,
            isGarden ? 1 : 0,
            0,
            r,
            c
          );
          plotIdx++;
        }
      }
    })();
  }

  // Initialize broker if empty
  const brokerCount = db.prepare('SELECT COUNT(*) as count FROM brokers').get().count;
  if (brokerCount === 0) {
    db.prepare(`
      INSERT INTO brokers (
        id, full_name, mobile, email, firm_name, city_area,
        rera_number, commission_type, commission_rate, tier, deals_closed_count,
        total_commission_earned, total_commission_paid, status
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      'brk_diamond_01',
      'Vikram Malhotra',
      '+91 98111 22334',
      'partner@apexrealty.com',
      'Diamond Channel Syndicate',
      'Western UP & NCR',
      'UPRERA/A/2025/8812',
      'PERCENTAGE',
      2.5,
      'Platinum',
      0,
      0,
      0,
      'ACTIVE'
    );
  }

  console.log('✅ SQLite Database initialized at:', dbPath);
}
