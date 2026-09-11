// Static reference data (states don't change), not user-entered content —
// CLAUDE.md's "never translate user-entered data" rule doesn't apply here;
// this is UI chrome for the state <select>, translated like any other label.
export interface IndianState {
  code: string;
  nameEn: string;
  nameHi: string;
}

export const INDIAN_STATES: IndianState[] = [
  { code: 'AN', nameEn: 'Andaman and Nicobar Islands', nameHi: 'अंडमान और निकोबार द्वीप समूह' },
  { code: 'AP', nameEn: 'Andhra Pradesh', nameHi: 'आंध्र प्रदेश' },
  { code: 'AR', nameEn: 'Arunachal Pradesh', nameHi: 'अरुणाचल प्रदेश' },
  { code: 'AS', nameEn: 'Assam', nameHi: 'असम' },
  { code: 'BR', nameEn: 'Bihar', nameHi: 'बिहार' },
  { code: 'CH', nameEn: 'Chandigarh', nameHi: 'चंडीगढ़' },
  { code: 'CG', nameEn: 'Chhattisgarh', nameHi: 'छत्तीसगढ़' },
  { code: 'DH', nameEn: 'Dadra and Nagar Haveli and Daman and Diu', nameHi: 'दादरा और नगर हवेली और दमन और दीव' },
  { code: 'DL', nameEn: 'Delhi', nameHi: 'दिल्ली' },
  { code: 'GA', nameEn: 'Goa', nameHi: 'गोवा' },
  { code: 'GJ', nameEn: 'Gujarat', nameHi: 'गुजरात' },
  { code: 'HR', nameEn: 'Haryana', nameHi: 'हरियाणा' },
  { code: 'HP', nameEn: 'Himachal Pradesh', nameHi: 'हिमाचल प्रदेश' },
  { code: 'JK', nameEn: 'Jammu and Kashmir', nameHi: 'जम्मू और कश्मीर' },
  { code: 'JH', nameEn: 'Jharkhand', nameHi: 'झारखंड' },
  { code: 'KA', nameEn: 'Karnataka', nameHi: 'कर्नाटक' },
  { code: 'KL', nameEn: 'Kerala', nameHi: 'केरल' },
  { code: 'LA', nameEn: 'Ladakh', nameHi: 'लद्दाख' },
  { code: 'LD', nameEn: 'Lakshadweep', nameHi: 'लक्षद्वीप' },
  { code: 'MP', nameEn: 'Madhya Pradesh', nameHi: 'मध्य प्रदेश' },
  { code: 'MH', nameEn: 'Maharashtra', nameHi: 'महाराष्ट्र' },
  { code: 'MN', nameEn: 'Manipur', nameHi: 'मणिपुर' },
  { code: 'ML', nameEn: 'Meghalaya', nameHi: 'मेघालय' },
  { code: 'MZ', nameEn: 'Mizoram', nameHi: 'मिज़ोरम' },
  { code: 'NL', nameEn: 'Nagaland', nameHi: 'नागालैंड' },
  { code: 'OR', nameEn: 'Odisha', nameHi: 'ओडिशा' },
  { code: 'PY', nameEn: 'Puducherry', nameHi: 'पुडुचेरी' },
  { code: 'PB', nameEn: 'Punjab', nameHi: 'पंजाब' },
  { code: 'RJ', nameEn: 'Rajasthan', nameHi: 'राजस्थान' },
  { code: 'SK', nameEn: 'Sikkim', nameHi: 'सिक्किम' },
  { code: 'TN', nameEn: 'Tamil Nadu', nameHi: 'तमिलनाडु' },
  { code: 'TS', nameEn: 'Telangana', nameHi: 'तेलंगाना' },
  { code: 'TR', nameEn: 'Tripura', nameHi: 'त्रिपुरा' },
  { code: 'UP', nameEn: 'Uttar Pradesh', nameHi: 'उत्तर प्रदेश' },
  { code: 'UK', nameEn: 'Uttarakhand', nameHi: 'उत्तराखंड' },
  { code: 'WB', nameEn: 'West Bengal', nameHi: 'पश्चिम बंगाल' },
];
