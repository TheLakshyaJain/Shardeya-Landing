import { db } from './db.js';

console.log('\n================== SHARDEYA DATABASE SUMMARY ==================\n');

const tables = [
  'users',
  'projects',
  'plots',
  'plot_sales',
  'payment_schedules',
  'payment_records',
  'leads',
  'interactions',
  'brokers',
  'commission_vouchers',
  'calendar_events'
];

for (const table of tables) {
  const count = db.prepare(`SELECT COUNT(*) as count FROM ${table}`).get().count;
  console.log(`📌 Table: ${table.padEnd(20)} | Rows: ${count}`);
}

console.log('\n------------------ CURRENT PROJECTS ------------------');
const projects = db.prepare('SELECT id, name, rera_number, city, declared_plot_count FROM projects').all();
console.table(projects);

console.log('------------------ USERS / AUTH ------------------');
const users = db.prepare('SELECT id, name, email, role, organization FROM users').all();
console.table(users);

console.log('------------------ PLOTS BREAKDOWN ------------------');
const plotSummary = db.prepare(`
  SELECT status, COUNT(*) as count 
  FROM plots 
  GROUP BY status
`).all();
console.table(plotSummary);

console.log('===============================================================\n');
