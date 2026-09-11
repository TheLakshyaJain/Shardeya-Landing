-- B-11 §17.2 system-default templates (org_id NULL, visible to every
-- tenant). BOOKING_CONFIRMATION is deliberately not seeded this round (see
-- CLAUDE.md -- it mirrors the §22.4 WhatsApp confirmation, which is M-06's
-- own second-half scope). Deliberately no {{org.logoUrl}} <img> anywhere in
-- these bodies -- a text-based letterhead (org name + address prominently)
-- is what B-11 §10's "org with no logo -> text fallback" edge case actually
-- needs, and using text unconditionally for every org sidesteps needing an
-- {{#if}} construct the sandbox grammar deliberately doesn't have (see
-- TemplateRenderer's own javadoc: two constructs only, interpolation and
-- #each -- no conditionals, matching B-11 §11 "no loops beyond the provided
-- collections" taken as the general spirit of "stay minimal").
INSERT INTO document_template (id, org_id, doc_type, name, language, header_html, body_html, footer_html, variables, version, is_active) VALUES
(gen_random_uuid(), NULL, 'ALLOTMENT_LETTER', 'Standard Allotment Letter', 'en',
$html$<div class="letterhead"><h1>{{org.name}}</h1><p>{{org.address}} &#183; {{org.phone}}</p></div><hr/><p style="text-align:right;">Date: {{document.generatedDate}}</p><h2 style="text-align:center;">ALLOTMENT LETTER</h2><p style="text-align:center;">Document No: {{document.number}}</p>$html$,
$html$<p>To,<br/>{{buyer.name}}<br/>Mobile: {{buyer.mobile}}</p><p>Dear {{buyer.name}},</p><p>We are pleased to confirm the allotment of the following plot in our project <strong>{{project.name}}</strong>, located at {{project.address}}:</p><table><tr><th>Plot Number</th><td>{{plot.number}}</td></tr><tr><th>Area</th><td>{{plot.areaValue}} {{plot.areaUnit}} ({{plot.areaSqft}} sq.ft.)</td></tr><tr><th>Facing</th><td>{{plot.facing}}</td></tr><tr><th>Deal Value</th><td>Rs. {{sale.dealValue}} ({{sale.dealValueWords}})</td></tr><tr><th>Purchase Date</th><td>{{sale.purchaseDate}}</td></tr><tr><th>Payment Type</th><td>{{sale.paymentType}}</td></tr></table><h3>Payment Schedule</h3><table><tr><th>Instalment</th><th>Amount (Rs.)</th><th>Due Date</th></tr>{{#each schedule}}<tr><td>{{label}}</td><td>{{amount}}</td><td>{{dueDate}}</td></tr>{{/each}}</table><p>This allotment is subject to the terms and conditions of the sale agreement executed between the parties. Please retain this letter for your records.</p>$html$,
$html$<p>For {{org.name}}</p><p style="margin-top:48px;">Authorised Signatory</p>$html$,
'[]', 0, true),
(gen_random_uuid(), NULL, 'PAYMENT_RECEIPT', 'Standard Payment Receipt', 'en',
$html$<div class="letterhead"><h1>{{org.name}}</h1><p>{{org.address}} &#183; {{org.phone}}</p></div><hr/><h2 style="text-align:center;">PAYMENT RECEIPT</h2><p style="text-align:center;">Receipt No: {{document.number}} &#183; Date: {{payment.paidOn}}</p>$html$,
$html$<p>Received with thanks from <strong>{{buyer.name}}</strong> (Mobile: {{buyer.mobile}}) a sum of <strong>Rs. {{payment.amount}}</strong> ({{payment.amountWords}}) towards plot <strong>{{plot.number}}</strong> in project <strong>{{project.name}}</strong>.</p><table><tr><th>Mode</th><td>{{payment.mode}}</td></tr><tr><th>Reference</th><td>{{payment.reference}}</td></tr><tr><th>Total Paid to Date</th><td>Rs. {{payment.totalPaid}}</td></tr><tr><th>Balance Remaining</th><td>Rs. {{payment.balanceDue}}</td></tr></table>$html$,
$html$<p>For {{org.name}}</p><p style="margin-top:48px;">Authorised Signatory</p>$html$,
'[]', 0, true),
(gen_random_uuid(), NULL, 'DEMAND_LETTER', 'Standard Demand Letter', 'en',
$html$<div class="letterhead"><h1>{{org.name}}</h1><p>{{org.address}} &#183; {{org.phone}}</p></div><hr/><h2 style="text-align:center;">PAYMENT DEMAND LETTER</h2><p style="text-align:center;">Document No: {{document.number}} &#183; Date: {{document.generatedDate}}</p>$html$,
$html$<p>To,<br/>{{buyer.name}}<br/>Mobile: {{buyer.mobile}}</p><p>Dear {{buyer.name}},</p><p>This is to bring to your attention that the following instalment(s) for plot <strong>{{plot.number}}</strong> in project <strong>{{project.name}}</strong> remain unpaid:</p><table><tr><th>Instalment</th><th>Amount Due (Rs.)</th><th>Due Date</th><th>Days Overdue</th></tr>{{#each overdue}}<tr><td>{{label}}</td><td>{{amount}}</td><td>{{dueDate}}</td><td>{{daysOverdue}}</td></tr>{{/each}}</table><p>Total amount due: <strong>Rs. {{demand.totalOverdueAmount}}</strong> ({{demand.totalOverdueAmountWords}})</p><p>We request you to clear the above dues at the earliest to avoid any inconvenience. For any queries, please contact us at {{org.phone}}.</p>$html$,
$html$<p>For {{org.name}}</p><p style="margin-top:48px;">Authorised Signatory</p>$html$,
'[]', 0, true)
ON CONFLICT DO NOTHING;
