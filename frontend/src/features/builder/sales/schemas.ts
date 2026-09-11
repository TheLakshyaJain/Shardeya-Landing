import { z } from 'zod';
import type { TFunction } from 'i18next';

// Mirrors backend/.../dto/SaleCreateRequest.java's Bean Validation
// constraints (B-04 §11), sourced from the same "errors" namespace a
// backend validation failure would use.
const e = (t: TFunction, key: string) => t(key, { ns: 'errors' });

export function buildSaleSchema(t: TFunction) {
  return z.object({
    buyerName: z.string().min(2, e(t, 'sale.buyerNameInvalid')).max(120, e(t, 'sale.buyerNameInvalid')),
    buyerMobile: z.string().regex(/^[6-9]\d{9}$/, e(t, 'sale.buyerMobileInvalid')),
    buyerEmail: z.string().email(e(t, 'email.invalid')).optional().or(z.literal('')),
    buyerGovIdType: z.enum(['AADHAAR', 'PAN', 'PASSPORT', 'VOTER_ID', 'DL']).optional(),
    buyerGovIdNumber: z.string().optional().or(z.literal('')),
    buyerWhatsappOptIn: z.boolean().optional(),
    purchaseDate: z.string().min(1, e(t, 'sale.purchaseDateRequired')),
    dealValue: z.number({ message: e(t, 'sale.dealValueRequired') }).positive(e(t, 'sale.dealValueInvalid')),
    // Only an in-system (registered) broker can be attributed to a sale --
    // the free-text external-broker path was removed from this wizard (see
    // SaleWizard's own comment), so no externalBrokerName/Mobile fields
    // exist here. brokerCommissionAmount is kept: it's still the fallback
    // manual figure B-14 §10 requires when a real broker has no resolvable
    // commission config at all.
    brokerPartnerId: z.string().optional(),
    brokerCommissionAmount: z.number().optional(),
    paymentType: z.enum(['LUMP_SUM', 'INSTALMENT'], { message: e(t, 'sale.paymentTypeRequired') }),
    schedule: z
      .array(
        z.object({
          label: z.string().max(80, e(t, 'schedule.labelInvalid')).optional().or(z.literal('')),
          amount: z.number({ message: e(t, 'schedule.amountRequired') }).positive(e(t, 'schedule.amountInvalid')),
          dueDate: z.string().min(1, e(t, 'schedule.dueDateRequired')),
        }),
      )
      .min(1, e(t, 'sale.scheduleRequired')),
  });
}

export type SaleFormValues = z.infer<ReturnType<typeof buildSaleSchema>>;
