import React, { useState } from 'react';
import { 
  Calculator, ArrowRightLeft, Percent, Landmark, 
  Layers, Compass, DollarSign, CheckCircle2, ChevronRight
} from 'lucide-react';
import { useLanguage } from '../../../context/LanguageContext';

export const CrmCalculatorsPage: React.FC = () => {
  const { language } = useLanguage();
  const isHi = language === 'hi';

  const [activeTab, setActiveTab] = useState<'area' | 'stamp' | 'emi' | 'gdv'>('area');

  // 1. Land Area Converter State
  const [areaValue, setAreaValue] = useState<number>(1);
  const [fromUnit, setFromUnit] = useState<string>('BIGHA_UP');

  // Conversion factors to Square Feet
  const SQFT_FACTORS: Record<string, number> = {
    BIGHA_UP: 27000,      // UP Pucca Bigha (3,000 Sq Yards = 27,000 Sq Ft)
    BIGHA_HR: 9075,       // Haryana / Punjab Kham Bigha (9,075 Sq Ft)
    BIGHA_RJ: 17424,      // Rajasthan Bigha (1,936 Sq Yards = 17,424 Sq Ft)
    BISWA_UP: 1350,       // 1/20th of UP Bigha = 1,350 Sq Ft
    ACRE: 43560,          // 1 Acre = 43,560 Sq Ft
    GAJ: 9,               // 1 Gaj (Sq Yard) = 9 Sq Ft
    SQ_FT: 1,
    SQ_MTR: 10.7639,      // 1 Sq Mtr = 10.7639 Sq Ft
    HECTARE: 107639       // 1 Hectare = 10,000 Sq Mtrs ~ 107,639 Sq Ft
  };

  const currentSqft = (areaValue || 0) * (SQFT_FACTORS[fromUnit] || 1);

  // 2. Stamp Duty Calculator State
  const [propertyValue, setPropertyValue] = useState<number>(5000000);
  const [stateCode, setStateCode] = useState<string>('UP');
  const [buyerGender, setBuyerGender] = useState<'MALE' | 'FEMALE' | 'JOINT'>('MALE');

  const getStampRates = () => {
    switch (stateCode) {
      case 'UP':
        const upDuty = buyerGender === 'FEMALE' ? 6.0 : 7.0;
        return { stampDutyPercent: upDuty, regPercent: 1.0, name: 'Uttar Pradesh (UP)' };
      case 'HR':
        const hrDuty = buyerGender === 'FEMALE' ? 5.0 : buyerGender === 'JOINT' ? 6.0 : 7.0;
        return { stampDutyPercent: hrDuty, regPercent: 1.0, name: 'Haryana (HR)' };
      case 'DL':
        const dlDuty = buyerGender === 'FEMALE' ? 4.0 : 6.0;
        return { stampDutyPercent: dlDuty, regPercent: 1.0, name: 'Delhi NCR' };
      case 'MH':
        return { stampDutyPercent: 6.0, regPercent: 1.0, name: 'Maharashtra' };
      default:
        return { stampDutyPercent: 6.0, regPercent: 1.0, name: 'Standard' };
    }
  };

  const rates = getStampRates();
  const stampDutyAmount = (propertyValue * rates.stampDutyPercent) / 100;
  const regAmount = (propertyValue * rates.regPercent) / 100;
  const totalGovtOutlay = stampDutyAmount + regAmount;
  const totalCostOfAcquisition = propertyValue + totalGovtOutlay;

  // 3. EMI Calculator State
  const [loanAmount, setLoanAmount] = useState<number>(4000000);
  const [interestRate, setInterestRate] = useState<number>(8.5);
  const [tenureYears, setTenureYears] = useState<number>(15);

  const calculateEmi = () => {
    const monthlyRate = interestRate / 12 / 100;
    const totalMonths = tenureYears * 12;
    if (monthlyRate === 0) return loanAmount / totalMonths;
    const emi = (loanAmount * monthlyRate * Math.pow(1 + monthlyRate, totalMonths)) / (Math.pow(1 + monthlyRate, totalMonths) - 1);
    return Math.round(emi);
  };

  const monthlyEmi = calculateEmi();
  const totalEmiPayment = monthlyEmi * tenureYears * 12;
  const totalInterestPayable = totalEmiPayment - loanAmount;

  // 4. GDV Plotted Yield Calculator State
  const [landAreaValue, setLandAreaValue] = useState<number>(40);
  const [landUnit, setLandUnit] = useState<string>('BIGHA_UP');
  const [openSpacePercent, setOpenSpacePercent] = useState<number>(40); // 40% roads/parks/STP
  const [avgSellingRate, setAvgSellingRate] = useState<number>(2200);

  const grossSqft = landAreaValue * (SQFT_FACTORS[landUnit] || 27000);
  const saleableSqft = grossSqft * ((100 - openSpacePercent) / 100);
  const saleableGaj = saleableSqft / 9;
  const projectedGDV = saleableSqft * avgSellingRate;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2">
          <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-forest/10 text-forest border border-forest/20 tracking-wide uppercase">
            {isHi ? 'रियल एस्टेट टूल्स' : 'Cadastral & Fiscal Engineering'}
          </span>
        </div>
        <h1 className="text-2xl sm:text-3xl font-serif font-bold text-espresso-950 mt-1">
          {isHi ? 'जमीन व वित्तीय कैलकुलेटर' : 'Indian Land & Financial Calculators'}
        </h1>
        <p className="text-sm text-espresso-700">
          {isHi 
            ? 'बीघा-गज-एकड़ रूपांतरण, राज्यवार स्टांप ड्यूटी, ईएमआई व टाउनशिप जीडीवी यील्ड विश्लेषण।' 
            : 'Multi-regional land cadastral converter, statutory state stamp tariffs, EMI amortization, and Gross Development Value (GDV) yield analyzer.'}
        </p>
      </div>

      {/* Tabs */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { id: 'area' as const, label: isHi ? 'भू-क्षेत्र कनवर्टर' : 'Land Area Converter', icon: ArrowRightLeft, desc: 'Bigha, Gaj, Biswa, Acre' },
          { id: 'stamp' as const, label: isHi ? 'स्टांप व रजिस्ट्री शुल्क' : 'Stamp Duty Tariffs', icon: Landmark, desc: 'UP, HR, Delhi, MH' },
          { id: 'emi' as const, label: isHi ? 'लोन / ईएमआई' : 'Loan & EMI Planner', icon: Percent, desc: 'Amortization & Interest' },
          { id: 'gdv' as const, label: isHi ? 'टाउनशिप यील्ड (GDV)' : 'Township GDV Yield', icon: Layers, desc: 'Plotted Net Saleable Area' },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`p-4 rounded-xl text-left border transition-all ${
              activeTab === tab.id
                ? 'bg-white border-forest shadow-md ring-1 ring-forest/20'
                : 'bg-white border-sand-300 hover:border-sand-400'
            }`}
          >
            <div className="flex items-center justify-between text-espresso-600">
              <tab.icon className={`w-5 h-5 ${activeTab === tab.id ? 'text-forest' : 'text-espresso-400'}`} />
            </div>
            <div className={`mt-2 font-serif font-bold text-sm ${activeTab === tab.id ? 'text-forest' : 'text-espresso-950'}`}>
              {tab.label}
            </div>
            <div className="text-[11px] text-espresso-500 mt-0.5">{tab.desc}</div>
          </button>
        ))}
      </div>

      {/* Calculator Body */}
      {activeTab === 'area' && (
        <div className="p-6 rounded-2xl bg-white border border-sand-300 shadow-sm space-y-6">
          <div>
            <h3 className="font-serif font-bold text-lg text-espresso-950">
              Multi-Regional Indian Land Cadastral Unit Converter
            </h3>
            <p className="text-xs text-espresso-600">
              Converts state-specific customary land measurements with exact mathematical precision.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-xl">
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">Enter Quantity</label>
              <input
                type="number"
                min="0"
                step="any"
                value={areaValue}
                onChange={e => setAreaValue(parseFloat(e.target.value) || 0)}
                className="w-full px-3 py-2 text-base font-bold bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest font-mono"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">Source Measurement Unit</label>
              <select
                value={fromUnit}
                onChange={e => setFromUnit(e.target.value)}
                className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 focus:border-forest font-medium"
              >
                <option value="BIGHA_UP">UP Pucca Bigha (27,000 sq ft / 3,000 Gaj)</option>
                <option value="BIGHA_HR">Haryana / Punjab Bigha (9,075 sq ft)</option>
                <option value="BIGHA_RJ">Rajasthan Bigha (17,424 sq ft / 1,936 Gaj)</option>
                <option value="BISWA_UP">UP Biswa (1,350 sq ft)</option>
                <option value="GAJ">Gaj / Square Yards (9 sq ft)</option>
                <option value="ACRE">Acre (43,560 sq ft / 4,840 Gaj)</option>
                <option value="SQ_FT">Square Feet (Sq Ft)</option>
                <option value="SQ_MTR">Square Meters (10.7639 sq ft)</option>
                <option value="HECTARE">Hectare (10,000 sq mtrs)</option>
              </select>
            </div>
          </div>

          {/* Results Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3 pt-4 border-t border-sand-200">
            <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
              <span className="text-xs text-espresso-500 font-medium">Gaj (Square Yards)</span>
              <div className="text-xl font-bold font-mono text-forest mt-1">
                {(currentSqft / 9).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
              <span className="text-[10px] text-espresso-400">Standard residential metric</span>
            </div>

            <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
              <span className="text-xs text-espresso-500 font-medium">Square Feet (Sq Ft)</span>
              <div className="text-xl font-bold font-mono text-espresso-950 mt-1">
                {currentSqft.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
              <span className="text-[10px] text-espresso-400">RERA declared carpet area</span>
            </div>

            <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
              <span className="text-xs text-espresso-500 font-medium">UP Pucca Bigha</span>
              <div className="text-xl font-bold font-mono text-espresso-950 mt-1">
                {(currentSqft / 27000).toLocaleString('en-IN', { maximumFractionDigits: 4 })}
              </div>
              <span className="text-[10px] text-espresso-400">1 Bigha = 20 Biswa</span>
            </div>

            <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
              <span className="text-xs text-espresso-500 font-medium">Acres</span>
              <div className="text-xl font-bold font-mono text-espresso-950 mt-1">
                {(currentSqft / 43560).toLocaleString('en-IN', { maximumFractionDigits: 4 })}
              </div>
              <span className="text-[10px] text-espresso-400">4,840 Gaj / Acre</span>
            </div>

            <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
              <span className="text-xs text-espresso-500 font-medium">Square Meters</span>
              <div className="text-xl font-bold font-mono text-espresso-950 mt-1">
                {(currentSqft / 10.7639).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
              </div>
              <span className="text-[10px] text-espresso-400">Official registry unit</span>
            </div>

            <div className="p-3 rounded-xl bg-sand-50 border border-sand-200">
              <span className="text-xs text-espresso-500 font-medium">Hectares</span>
              <div className="text-xl font-bold font-mono text-espresso-950 mt-1">
                {(currentSqft / 107639).toLocaleString('en-IN', { maximumFractionDigits: 4 })}
              </div>
              <span className="text-[10px] text-espresso-400">Revenue record metric</span>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'stamp' && (
        <div className="p-6 rounded-2xl bg-white border border-sand-300 shadow-sm space-y-6">
          <div>
            <h3 className="font-serif font-bold text-lg text-espresso-950">
              State Stamp Duty & Registration Tariff Calculator
            </h3>
            <p className="text-xs text-espresso-600">
              Includes female buyer concessions and sub-registrar deed registration fees.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">Agreement / Circle Value (₹) *</label>
              <input
                type="number"
                value={propertyValue}
                onChange={e => setPropertyValue(parseFloat(e.target.value) || 0)}
                className="w-full px-3 py-2 text-base font-bold bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 font-mono"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">State Jurisdiction</label>
              <select
                value={stateCode}
                onChange={e => setStateCode(e.target.value)}
                className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 font-medium"
              >
                <option value="UP">Uttar Pradesh (UP)</option>
                <option value="HR">Haryana (HR)</option>
                <option value="DL">Delhi NCT</option>
                <option value="MH">Maharashtra (MH)</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">Primary Allottee Category</label>
              <select
                value={buyerGender}
                onChange={e => setBuyerGender(e.target.value as any)}
                className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 font-medium"
              >
                <option value="MALE">Male (Standard)</option>
                <option value="FEMALE">Female (1% Concession in UP/HR/DL)</option>
                <option value="JOINT">Joint Ownership (Male + Female)</option>
              </select>
            </div>
          </div>

          {/* Breakdown Card */}
          <div className="p-5 rounded-xl bg-sand-50 border border-sand-200 max-w-xl space-y-3">
            <div className="flex justify-between text-sm">
              <span className="text-espresso-600">Base Unit Value:</span>
              <span className="font-mono font-semibold text-espresso-950">₹{propertyValue.toLocaleString('en-IN')}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-espresso-600">Stamp Duty ({rates.stampDutyPercent}% in {rates.name}):</span>
              <span className="font-mono font-semibold text-espresso-950">₹{Math.round(stampDutyAmount).toLocaleString('en-IN')}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-espresso-600">Sub-Registrar Registration Fee ({rates.regPercent}%):</span>
              <span className="font-mono font-semibold text-espresso-950">₹{Math.round(regAmount).toLocaleString('en-IN')}</span>
            </div>
            <div className="pt-3 border-t border-sand-300 flex justify-between text-base font-bold text-forest">
              <span>Total Statutory Govt Outlay:</span>
              <span className="font-mono">₹{Math.round(totalGovtOutlay).toLocaleString('en-IN')}</span>
            </div>
            <div className="pt-2 border-t border-sand-300 flex justify-between text-lg font-serif font-bold text-espresso-950">
              <span>Total Property Acquisition Cost:</span>
              <span className="font-mono">₹{Math.round(totalCostOfAcquisition).toLocaleString('en-IN')}</span>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'emi' && (
        <div className="p-6 rounded-2xl bg-white border border-sand-300 shadow-sm space-y-6">
          <div>
            <h3 className="font-serif font-bold text-lg text-espresso-950">
              Home Loan & Plot Loan EMI Amortization
            </h3>
            <p className="text-xs text-espresso-600">
              Calculates monthly financial commitments and total interest payable.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">Loan Amount (₹)</label>
              <input
                type="number"
                value={loanAmount}
                onChange={e => setLoanAmount(parseFloat(e.target.value) || 0)}
                className="w-full px-3 py-2 text-base font-bold bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 font-mono"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">Interest Rate (% p.a.)</label>
              <input
                type="number"
                step="0.1"
                value={interestRate}
                onChange={e => setInterestRate(parseFloat(e.target.value) || 0)}
                className="w-full px-3 py-2 text-base font-bold bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 font-mono"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">Tenure (Years)</label>
              <input
                type="number"
                value={tenureYears}
                onChange={e => setTenureYears(parseInt(e.target.value) || 1)}
                className="w-full px-3 py-2 text-base font-bold bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 font-mono"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-4 border-t border-sand-200">
            <div className="p-4 rounded-xl bg-forest/5 border border-forest/20">
              <span className="text-xs text-forest font-semibold uppercase">Monthly EMI</span>
              <div className="text-3xl font-serif font-bold text-forest mt-1 font-mono">
                ₹{monthlyEmi.toLocaleString('en-IN')}
              </div>
              <span className="text-[11px] text-espresso-500 mt-1 block">For {tenureYears * 12} monthly installments</span>
            </div>

            <div className="p-4 rounded-xl bg-sand-50 border border-sand-200">
              <span className="text-xs text-espresso-600 font-semibold uppercase">Total Interest Payable</span>
              <div className="text-2xl font-serif font-bold text-amber-900 mt-1 font-mono">
                ₹{Math.round(totalInterestPayable).toLocaleString('en-IN')}
              </div>
              <span className="text-[11px] text-espresso-500 mt-1 block">{((totalInterestPayable / loanAmount) * 100).toFixed(1)}% of principal</span>
            </div>

            <div className="p-4 rounded-xl bg-sand-50 border border-sand-200">
              <span className="text-xs text-espresso-600 font-semibold uppercase">Total Repayment Amount</span>
              <div className="text-2xl font-serif font-bold text-espresso-950 mt-1 font-mono">
                ₹{Math.round(totalEmiPayment).toLocaleString('en-IN')}
              </div>
              <span className="text-[11px] text-espresso-500 mt-1 block">Principal + Cumulative Interest</span>
            </div>
          </div>
        </div>
      )}

      {activeTab === 'gdv' && (
        <div className="p-6 rounded-2xl bg-white border border-sand-300 shadow-sm space-y-6">
          <div>
            <h3 className="font-serif font-bold text-lg text-espresso-950">
              Township Plotted Land Yield & GDV Realization
            </h3>
            <p className="text-xs text-espresso-600">
              Models 60:40 plotted colony efficiency (roads, open parks, utilities) and estimates gross project realization.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">Total Land Parcel Size</label>
              <input
                type="number"
                value={landAreaValue}
                onChange={e => setLandAreaValue(parseFloat(e.target.value) || 0)}
                className="w-full px-3 py-2 text-base font-bold bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 font-mono"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">Land Unit</label>
              <select
                value={landUnit}
                onChange={e => setLandUnit(e.target.value)}
                className="w-full px-3 py-2 text-sm bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 font-medium"
              >
                <option value="BIGHA_UP">UP Pucca Bigha</option>
                <option value="ACRE">Acre</option>
                <option value="HECTARE">Hectare</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">RERA Infrastructure Open %</label>
              <input
                type="number"
                value={openSpacePercent}
                onChange={e => setOpenSpacePercent(parseFloat(e.target.value) || 40)}
                className="w-full px-3 py-2 text-base font-bold bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 font-mono"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-espresso-700 mb-1">Average Price / Sq Ft (₹)</label>
              <input
                type="number"
                value={avgSellingRate}
                onChange={e => setAvgSellingRate(parseFloat(e.target.value) || 0)}
                className="w-full px-3 py-2 text-base font-bold bg-white border border-sand-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-forest/20 font-mono"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-4 border-t border-sand-200">
            <div className="p-4 rounded-xl bg-sand-50 border border-sand-200">
              <span className="text-xs text-espresso-600 font-semibold uppercase">Net Saleable Plotted Area</span>
              <div className="text-2xl font-serif font-bold text-espresso-950 mt-1 font-mono">
                {Math.round(saleableSqft).toLocaleString('en-IN')} sqft
              </div>
              <span className="text-xs text-forest mt-0.5 block font-medium">~{Math.round(saleableGaj).toLocaleString('en-IN')} Gaj plotted inventory</span>
            </div>

            <div className="p-4 rounded-xl bg-sand-50 border border-sand-200">
              <span className="text-xs text-espresso-600 font-semibold uppercase">Roads, Parks & Green Space</span>
              <div className="text-2xl font-serif font-bold text-espresso-950 mt-1 font-mono">
                {Math.round(grossSqft * (openSpacePercent / 100)).toLocaleString('en-IN')} sqft
              </div>
              <span className="text-xs text-espresso-500 mt-0.5 block">{openSpacePercent}% statutory deduction</span>
            </div>

            <div className="p-4 rounded-xl bg-forest/5 border border-forest/20">
              <span className="text-xs text-forest font-bold uppercase">Estimated GDV Realization</span>
              <div className="text-3xl font-serif font-bold text-forest mt-1 font-mono">
                ₹{(projectedGDV / 10000000).toFixed(2)} Cr
              </div>
              <span className="text-xs text-espresso-600 mt-0.5 block">At ₹{avgSellingRate}/sqft realization</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
