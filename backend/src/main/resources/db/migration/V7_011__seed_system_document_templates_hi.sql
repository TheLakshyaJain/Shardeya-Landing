-- B-11 §11 "Devanagari in PDF... explicit test case" -- these Hindi bodies
-- are exactly what proves PdfRenderer's embedded Noto Sans Devanagari font
-- renders real conjuncts rather than boxes, since every static label here
-- is genuine Devanagari text, not a transliteration.
INSERT INTO document_template (id, org_id, doc_type, name, language, header_html, body_html, footer_html, variables, version, is_active) VALUES
(gen_random_uuid(), NULL, 'ALLOTMENT_LETTER', 'मानक आवंटन पत्र', 'hi',
$html$<div class="letterhead"><h1>{{org.name}}</h1><p>{{org.address}} &#183; {{org.phone}}</p></div><hr/><p style="text-align:right;">दिनांक: {{document.generatedDate}}</p><h2 style="text-align:center;">आवंटन पत्र</h2><p style="text-align:center;">दस्तावेज़ संख्या: {{document.number}}</p>$html$,
$html$<p>सेवा में,<br/>{{buyer.name}}<br/>मोबाइल: {{buyer.mobile}}</p><p>प्रिय {{buyer.name}},</p><p>हमें आपको यह सूचित करते हुए हर्ष हो रहा है कि हमारी परियोजना <strong>{{project.name}}</strong>, स्थित {{project.address}}, में निम्नलिखित प्लॉट आपके नाम आवंटित किया गया है:</p><table><tr><th>प्लॉट संख्या</th><td>{{plot.number}}</td></tr><tr><th>क्षेत्रफल</th><td>{{plot.areaValue}} {{plot.areaUnit}} ({{plot.areaSqft}} वर्ग फुट)</td></tr><tr><th>दिशा</th><td>{{plot.facing}}</td></tr><tr><th>सौदे की राशि</th><td>₹ {{sale.dealValue}} ({{sale.dealValueWords}})</td></tr><tr><th>खरीद की तारीख</th><td>{{sale.purchaseDate}}</td></tr><tr><th>भुगतान प्रकार</th><td>{{sale.paymentType}}</td></tr></table><h3>भुगतान अनुसूची</h3><table><tr><th>किस्त</th><th>राशि (₹)</th><th>देय तिथि</th></tr>{{#each schedule}}<tr><td>{{label}}</td><td>{{amount}}</td><td>{{dueDate}}</td></tr>{{/each}}</table><p>यह आवंटन दोनों पक्षों के बीच निष्पादित बिक्री समझौते की शर्तों के अधीन है। कृपया इस पत्र को अपने रिकॉर्ड के लिए सुरक्षित रखें।</p>$html$,
$html$<p>{{org.name}} की ओर से</p><p style="margin-top:48px;">अधिकृत हस्ताक्षरकर्ता</p>$html$,
'[]', 0, true),
(gen_random_uuid(), NULL, 'PAYMENT_RECEIPT', 'मानक भुगतान रसीद', 'hi',
$html$<div class="letterhead"><h1>{{org.name}}</h1><p>{{org.address}} &#183; {{org.phone}}</p></div><hr/><h2 style="text-align:center;">भुगतान रसीद</h2><p style="text-align:center;">रसीद संख्या: {{document.number}} &#183; तिथि: {{payment.paidOn}}</p>$html$,
$html$<p><strong>{{buyer.name}}</strong> (मोबाइल: {{buyer.mobile}}) से परियोजना <strong>{{project.name}}</strong> के प्लॉट <strong>{{plot.number}}</strong> के लिए <strong>₹ {{payment.amount}}</strong> ({{payment.amountWords}}) सहर्ष प्राप्त हुए।</p><table><tr><th>माध्यम</th><td>{{payment.mode}}</td></tr><tr><th>संदर्भ</th><td>{{payment.reference}}</td></tr><tr><th>अब तक कुल भुगतान</th><td>₹ {{payment.totalPaid}}</td></tr><tr><th>शेष राशि</th><td>₹ {{payment.balanceDue}}</td></tr></table>$html$,
$html$<p>{{org.name}} की ओर से</p><p style="margin-top:48px;">अधिकृत हस्ताक्षरकर्ता</p>$html$,
'[]', 0, true),
(gen_random_uuid(), NULL, 'DEMAND_LETTER', 'मानक मांग पत्र', 'hi',
$html$<div class="letterhead"><h1>{{org.name}}</h1><p>{{org.address}} &#183; {{org.phone}}</p></div><hr/><h2 style="text-align:center;">भुगतान मांग पत्र</h2><p style="text-align:center;">दस्तावेज़ संख्या: {{document.number}} &#183; तिथि: {{document.generatedDate}}</p>$html$,
$html$<p>सेवा में,<br/>{{buyer.name}}<br/>मोबाइल: {{buyer.mobile}}</p><p>प्रिय {{buyer.name}},</p><p>आपके ध्यान में लाया जाता है कि परियोजना <strong>{{project.name}}</strong> के प्लॉट <strong>{{plot.number}}</strong> के लिए निम्नलिखित किस्त(ें) अभी तक अवैतनिक हैं:</p><table><tr><th>किस्त</th><th>देय राशि (₹)</th><th>देय तिथि</th><th>विलंब दिन</th></tr>{{#each overdue}}<tr><td>{{label}}</td><td>{{amount}}</td><td>{{dueDate}}</td><td>{{daysOverdue}}</td></tr>{{/each}}</table><p>कुल देय राशि: <strong>₹ {{demand.totalOverdueAmount}}</strong> ({{demand.totalOverdueAmountWords}})</p><p>हम आपसे अनुरोध करते हैं कि किसी भी असुविधा से बचने के लिए उपरोक्त राशि शीघ्र चुका दें। किसी भी प्रश्न के लिए कृपया {{org.phone}} पर संपर्क करें।</p>$html$,
$html$<p>{{org.name}} की ओर से</p><p style="margin-top:48px;">अधिकृत हस्ताक्षरकर्ता</p>$html$,
'[]', 0, true)
ON CONFLICT DO NOTHING;
