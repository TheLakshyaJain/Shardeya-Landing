import express from 'express';
import { randomUUID } from 'node:crypto';

const app = express();
app.use(express.json());

const sentMessages = [];

app.get('/health', (_req, res) => res.status(200).json({ status: 'UP' }));

// Mimics the WhatsApp Business Cloud API's send-message response shape so the
// notification dispatcher (M-06) can be built and tested against something
// realistic without real WhatsApp credentials.
app.post('/v1/messages', (req, res) => {
  const id = `wamid.stub-${randomUUID()}`;
  sentMessages.push({ id, receivedAt: new Date().toISOString(), body: req.body });
  console.log(`[whatsapp-stub] queued ${id}`, JSON.stringify(req.body));
  res.status(200).json({ messaging_product: 'whatsapp', messages: [{ id }] });
});

// Dev-only inspection endpoint — not part of the real WhatsApp API.
app.get('/v1/messages', (_req, res) => res.status(200).json(sentMessages));

const port = process.env.PORT || 4001;
app.listen(port, () => console.log(`[whatsapp-stub] listening on ${port}`));
