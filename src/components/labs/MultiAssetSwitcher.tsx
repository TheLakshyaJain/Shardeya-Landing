import React, { useState } from 'react';
import { 
  Building2, Trees, Store, CheckCircle2, ShieldCheck, 
  ArrowUpRight, Info, Eye, Download, Check 
} from 'lucide-react';
import { audioHaptics } from '../../utils/audioHaptics';

type AssetType = 'plotted' | 'highrise' | 'commercial';

interface UnitItem {
  id: string;
  name: string;
  type: string;
  size: string;
  dimensions?: string;
  facing: string;
  price: string;
  status: 'available' | 'booked' | 'token_hold';
  buyer?: string;
  broker?: string;
  cpCommission: string;
  extraDetail: string;
}

export const MultiAssetSwitcher: React.FC = () => {
  const [activeAsset, setActiveAsset] = useState<AssetType>('plotted');
  const [selectedUnit, setSelectedUnit] = useState<UnitItem | null>(null);
  const [holdToast, setHoldToast] = useState<string | null>(null);

  const assetData: Record<AssetType, {
    title: string;
    location: string;
    badge: string;
    description: string;
    specs: string[];
    units: UnitItem[];
  }> = {
    plotted: {
      title: 'Apex Greens Township',
      location: 'Sector 84, Dwarka Expressway, Gurugram',
      badge: 'PLOTTED TOWNSHIP',
      description: 'Gated villa plot community with 60ft arterial sector roads, private club, and underground cabling.',
      specs: ['120 Total Plots', 'Far Index: 2.64', 'Vastu Compliant', 'Bank Approved: SBI/HDFC'],
      units: [
        {
          id: 'plt-101',
          name: 'Villa Plot #101',
          type: 'Villa Plot',
          size: '2,150 sq.ft (238 sq.yd)',
          dimensions: '30\'0" × 71\'8"',
          facing: 'North-East (Park View)',
          price: '₹1.85 Cr',
          status: 'available',
          cpCommission: '₹2.77 Lakh (1.5%)',
          extraDetail: 'Corner location with 40ft front road clearance',
        },
        {
          id: 'plt-102',
          name: 'Villa Plot #102',
          type: 'Villa Plot',
          size: '2,400 sq.ft (266 sq.yd)',
          dimensions: '32\'0" × 75\'0"',
          facing: 'East Facing',
          price: '₹2.05 Cr',
          status: 'booked',
          buyer: 'Ananya & Rohit Mehra',
          broker: 'Syndicate Prime (CP-102)',
          cpCommission: '₹3.07 Lakh (1.5%)',
          extraDetail: 'Allotment Letter Issued • 65% Payments Received',
        },
        {
          id: 'plt-103',
          name: 'Villa Plot #103',
          type: 'Club Facing Plot',
          size: '2,800 sq.ft (311 sq.yd)',
          dimensions: '35\'0" × 80\'0"',
          facing: 'North Facing (Club Access)',
          price: '₹2.45 Cr',
          status: 'token_hold',
          buyer: 'Token: Dr. Rajesh Talwar',
          broker: 'Aura Realty (CP-044)',
          cpCommission: '₹3.67 Lakh (1.5%)',
          extraDetail: '48-Hour Token Hold Active (₹1.00 Lakh Received)',
        },
        {
          id: 'plt-104',
          name: 'Villa Plot #104',
          type: 'Corner Park Plot',
          size: '2,400 sq.ft (266 sq.yd)',
          dimensions: '32\'0" × 75\'0"',
          facing: 'North-East Dual Road',
          price: '₹2.15 Cr',
          status: 'available',
          cpCommission: '₹3.22 Lakh (1.5%)',
          extraDetail: 'Highest demand inventory in Phase 1',
        },
        {
          id: 'plt-105',
          name: 'Villa Plot #105',
          type: 'Executive Plot',
          size: '3,200 sq.ft (355 sq.yd)',
          dimensions: '40\'0" × 80\'0"',
          facing: 'West Facing',
          price: '₹2.75 Cr',
          status: 'booked',
          buyer: 'Vikramaditya Singhania',
          broker: 'Apex Channel Partners',
          cpCommission: '₹4.12 Lakh (1.5%)',
          extraDetail: 'Construction NOC Released by Town Planning',
        },
        {
          id: 'plt-106',
          name: 'Villa Plot #106',
          type: 'Standard Plot',
          size: '1,950 sq.ft (216 sq.yd)',
          dimensions: '30\'0" × 65\'0"',
          facing: 'East Facing',
          price: '₹1.65 Cr',
          status: 'available',
          cpCommission: '₹2.47 Lakh (1.5%)',
          extraDetail: 'Adjacent to Community Gazebo & Walkway',
        },
      ],
    },
    highrise: {
      title: 'The Monarch Towers',
      location: 'Sector 150, Noida Expressway',
      badge: 'LUXURY HIGH-RISE',
      description: 'Twin 18-storey residential towers overlooking a 9-hole executive golf course with Olympic-size pool.',
      specs: ['144 Luxury Suites', 'Tower A & B', '3 & 4 BHK Duplexes', 'Mivan Monolithic Concrete'],
      units: [
        {
          id: 'apt-a204',
          name: 'Apt A-204 (Tower A)',
          type: '3 BHK Luxury Suite',
          size: '1,820 sq.ft (Carpet 1,320 sq.ft)',
          facing: 'Golf Course & Sunrise View',
          price: '₹1.65 Cr',
          status: 'available',
          cpCommission: '₹2.47 Lakh (1.5%)',
          extraDetail: '2 Dedicated Basement EV Parking Bays',
        },
        {
          id: 'apt-a801',
          name: 'Apt A-801 (Tower A)',
          type: '3 BHK + Servant',
          size: '2,150 sq.ft (Carpet 1,580 sq.ft)',
          facing: 'Central Greens Park View',
          price: '₹1.95 Cr',
          status: 'booked',
          buyer: 'Kapil Dev & Sunita Narang',
          broker: 'Capital Heights Realty',
          cpCommission: '₹2.92 Lakh (1.5%)',
          extraDetail: '8th Floor Slab Completed • Demand Letter Sent',
        },
        {
          id: 'apt-b502',
          name: 'Apt B-502 (Tower B)',
          type: '4 BHK Grand Duplex',
          size: '3,100 sq.ft (Carpet 2,340 sq.ft)',
          facing: 'Skyline & Golf View',
          price: '₹2.95 Cr',
          status: 'token_hold',
          buyer: 'Token: Arvind Goel',
          broker: 'Sole Syndicate CP',
          cpCommission: '₹4.42 Lakh (1.5%)',
          extraDetail: '48-Hour Token Hold • Digital Token Verified',
        },
        {
          id: 'apt-b1204',
          name: 'Apt B-1204 (Tower B)',
          type: '3 BHK Luxury Suite',
          size: '1,820 sq.ft (Carpet 1,320 sq.ft)',
          facing: 'East Facing Sunrise',
          price: '₹1.72 Cr',
          status: 'available',
          cpCommission: '₹2.58 Lakh (1.5%)',
          extraDetail: 'Higher Floor PLC included in list price',
        },
        {
          id: 'apt-b1801',
          name: 'Penthouse B-1801',
          type: 'Sky Penthouse + Private Terrace',
          size: '4,800 sq.ft (Carpet 3,650 sq.ft)',
          facing: '360° Panoramic Terrace',
          price: '₹4.85 Cr',
          status: 'booked',
          buyer: 'Siddharth Oberoi',
          broker: 'Apex High-Street Syndicate',
          cpCommission: '₹9.70 Lakh (2.0%)',
          extraDetail: 'Private Heated Plunge Pool & Deck',
        },
        {
          id: 'apt-a402',
          name: 'Apt A-402 (Tower A)',
          type: '3 BHK Executive',
          size: '1,920 sq.ft (Carpet 1,410 sq.ft)',
          facing: 'Club & Promenade View',
          price: '₹1.78 Cr',
          status: 'available',
          cpCommission: '₹2.67 Lakh (1.5%)',
          extraDetail: 'Modular Italian Kitchen Pre-fitted',
        },
      ],
    },
    commercial: {
      title: 'Galleria 84 Commercial Arcade',
      location: 'Golf Course Extension Road, Gurugram',
      badge: 'COMMERCIAL RETAIL',
      description: 'High-street commercial retail promenade with double-height showrooms, multiplex, and rooftop food court.',
      specs: ['68 Retail Units', 'Double-Height 18ft Ceilings', 'Catchment: 45,000 Families', 'Pre-Leased Brands'],
      units: [
        {
          id: 'com-g01',
          name: 'Showroom G-01 (Ground Floor)',
          type: 'Anchor Double-Height Showroom',
          size: '2,800 sq.ft (Frontage 45 ft)',
          facing: 'Main 60m Arterial Highway Road',
          price: '₹4.20 Cr',
          status: 'token_hold',
          buyer: 'Token: Reliance Retail Franchise',
          broker: 'Commercial Syndicate Partners',
          cpCommission: '₹8.40 Lakh (2.0%)',
          extraDetail: 'High-Footfall Corner Unit • 3 Phase 45kVA Power',
        },
        {
          id: 'com-g04',
          name: 'Shop G-04 (Ground Floor)',
          type: 'High Street Boutique Retail',
          size: '1,150 sq.ft (Frontage 22 ft)',
          facing: 'Central Water Fountain Plaza',
          price: '₹1.75 Cr',
          status: 'available',
          cpCommission: '₹2.62 Lakh (1.5%)',
          extraDetail: 'Zero Setback Direct Walk-in Access',
        },
        {
          id: 'com-f12',
          name: 'F&B Terrace Shop F-12',
          type: 'Rooftop Cafe & Brewery Space',
          size: '2,200 sq.ft + 1,200 sq.ft Terrace',
          facing: 'Open Sky Atrium View',
          price: '₹3.10 Cr',
          status: 'booked',
          buyer: 'DineCorp Hospitality LLP',
          broker: 'Metro Commercial CP',
          cpCommission: '₹6.20 Lakh (2.0%)',
          extraDetail: 'Gas Pipeline & Grease Trap Pre-Installed',
        },
        {
          id: 'com-g08',
          name: 'Shop G-08 (Ground Floor)',
          type: 'Jewellery / Luxury Retail Space',
          size: '1,450 sq.ft (Frontage 25 ft)',
          facing: 'Promenade Arcade Facing',
          price: '₹2.25 Cr',
          status: 'available',
          cpCommission: '₹3.37 Lakh (1.5%)',
          extraDetail: 'Reinforced Vault Flooring & CCTV Conduit',
        },
        {
          id: 'com-f05',
          name: 'Office Suite F-05 (First Floor)',
          type: 'Corporate Clinic / Office Suite',
          size: '950 sq.ft',
          facing: 'Elevator Lobby Adjacent',
          price: '₹1.15 Cr',
          status: 'available',
          cpCommission: '₹1.72 Lakh (1.5%)',
          extraDetail: 'Direct Elevator & Escalator Connectivity',
        },
        {
          id: 'com-g14',
          name: 'Shop G-14 (Ground Floor)',
          type: 'QSR Food Outlet',
          size: '800 sq.ft + Outdoor Seating',
          facing: 'Food Street Entry',
          price: '₹1.35 Cr',
          status: 'booked',
          buyer: 'Chai Point Franchise Partner',
          broker: 'Retail Edge CP',
          cpCommission: '₹2.02 Lakh (1.5%)',
          extraDetail: 'High Velocity Footfall Entry Gate',
        },
      ],
    },
  };

  const currentProject = assetData[activeAsset];

  const handleAssetChange = (type: AssetType) => {
    setActiveAsset(type);
    setSelectedUnit(null);
    audioHaptics.playSwitch();
  };

  const handleSelectUnit = (unit: UnitItem) => {
    setSelectedUnit(unit);
    audioHaptics.playClick();
  };

  const handleHoldToken = (unitName: string) => {
    audioHaptics.playSuccess();
    setHoldToast(`Put 48-Hour Token Hold on ${unitName}`);
    setTimeout(() => setHoldToast(null), 3000);
  };

  return (
    <div className="w-full text-left space-y-6">
      {/* Header & Segmented Controller */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-100 border border-slate-200 text-slate-700 text-xs font-mono font-bold uppercase tracking-wider mb-2">
            <Building2 className="w-3.5 h-3.5 text-emerald-600" />
            <span>Multi-Asset Inventory Architecture</span>
          </div>
          <h3 className="text-2xl sm:text-3xl font-serif font-bold text-slate-950">
            One Platform for Plots, Luxury Towers & Commercial
          </h3>
          <p className="text-sm text-slate-600 font-sans mt-1">
            Toggle between different real estate asset classes to see how Shardeya adapts layout matrices, dimensions, and commission structures.
          </p>
        </div>

        {/* 3-Way Asset Class Switcher */}
        <div className="flex items-center p-1 rounded-xl bg-slate-200/80 border border-slate-300 gap-1 text-xs font-sans font-bold">
          <button
            onClick={() => handleAssetChange('plotted')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-lg transition-all ${
              activeAsset === 'plotted'
                ? 'bg-white text-emerald-900 shadow-warm-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Trees className="w-4 h-4 text-emerald-600" />
            <span>Plotted Township</span>
          </button>

          <button
            onClick={() => handleAssetChange('highrise')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-lg transition-all ${
              activeAsset === 'highrise'
                ? 'bg-white text-emerald-900 shadow-warm-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Building2 className="w-4 h-4 text-emerald-600" />
            <span>Luxury High-Rise</span>
          </button>

          <button
            onClick={() => handleAssetChange('commercial')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-lg transition-all ${
              activeAsset === 'commercial'
                ? 'bg-white text-emerald-900 shadow-warm-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Store className="w-4 h-4 text-emerald-600" />
            <span>Commercial Arcade</span>
          </button>
        </div>
      </div>

      {/* Project Metadata Banner */}
      <div className="p-5 rounded-2xl bg-white border border-slate-200 shadow-warm-sm flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5 mb-1">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
            <h4 className="font-serif font-bold text-xl text-slate-950">{currentProject.title}</h4>
            <span className="font-mono text-[10px] px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-bold border border-slate-200">
              {currentProject.badge}
            </span>
          </div>
          <p className="text-xs text-slate-600 font-sans">{currentProject.location} • {currentProject.description}</p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {currentProject.specs.map((spec, sIdx) => (
            <span key={sIdx} className="px-2.5 py-1 rounded-lg bg-slate-50 border border-slate-200 text-slate-700 font-mono text-xs">
              {spec}
            </span>
          ))}
        </div>
      </div>

      {/* Main Interactive Grid & Drawer */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left: 6 Interactive Unit Cards */}
        <div className="lg:col-span-8 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {currentProject.units.map((unit) => {
            const isSelected = selectedUnit?.id === unit.id;
            const statusBadge = 
              unit.status === 'available'
                ? 'bg-emerald-100 text-emerald-900 border-emerald-300'
                : unit.status === 'booked'
                ? 'bg-slate-200 text-slate-800 border-slate-300'
                : 'bg-amber-100 text-amber-900 border-amber-300 animate-pulse';

            return (
              <div
                key={unit.id}
                onClick={() => handleSelectUnit(unit)}
                className={`p-4 rounded-xl border transition-all cursor-pointer text-left ${
                  isSelected
                    ? 'bg-white border-emerald-500 ring-2 ring-emerald-500/20 shadow-warm-md transform -translate-y-0.5'
                    : 'bg-white hover:bg-slate-50 border-slate-200 shadow-warm-sm hover:border-slate-300'
                }`}
              >
                <div className="flex items-center justify-between mb-1.5">
                  <span className="font-mono text-xs font-bold text-slate-800">{unit.name}</span>
                  <span className={`text-[9px] font-mono px-1.5 py-0.5 rounded font-bold uppercase border ${statusBadge}`}>
                    {unit.status.replace('_', ' ')}
                  </span>
                </div>

                <div className="text-xs font-sans text-slate-500 mb-2">{unit.type}</div>

                <div className="text-lg font-serif font-bold text-slate-950 mb-1">
                  {unit.price}
                </div>

                <div className="text-[11px] text-slate-600 font-sans space-y-0.5 border-t border-slate-100 pt-2">
                  <div>📐 {unit.size}</div>
                  {unit.dimensions && <div>📏 {unit.dimensions}</div>}
                  <div className="truncate text-slate-500">🧭 {unit.facing}</div>
                </div>

                <div className="mt-3 pt-2 border-t border-slate-100 flex items-center justify-between text-[10px] font-mono">
                  <span className="text-slate-400">CP Cut:</span>
                  <span className="text-emerald-700 font-bold">{unit.cpCommission}</span>
                </div>
              </div>
            );
          })}
        </div>

        {/* Right: Selected Unit Deep-Dive Inspection Drawer */}
        <div className="lg:col-span-4">
          {selectedUnit ? (
            <div className="p-6 rounded-2xl bg-white border border-slate-200 shadow-warm-md text-left space-y-4 animate-fadeIn">
              <div className="flex items-center justify-between pb-3 border-b border-slate-200">
                <div>
                  <span className="font-mono text-[10px] uppercase tracking-wider text-slate-400">UNIT INSPECTION</span>
                  <h4 className="font-serif font-bold text-xl text-slate-950">{selectedUnit.name}</h4>
                </div>
                <div className="text-right">
                  <div className="font-serif font-bold text-xl text-emerald-800">{selectedUnit.price}</div>
                  <span className="font-mono text-[10px] text-slate-500">Total Consideration</span>
                </div>
              </div>

              <div className="space-y-2 text-xs font-sans text-slate-700">
                <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1.5">
                  <div className="flex justify-between">
                    <span className="text-slate-500">Classification:</span>
                    <strong className="text-slate-900">{selectedUnit.type}</strong>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-500">Total Built-Up:</span>
                    <strong className="text-slate-900">{selectedUnit.size}</strong>
                  </div>
                  {selectedUnit.dimensions && (
                    <div className="flex justify-between">
                      <span className="text-slate-500">Survey Dimensions:</span>
                      <strong className="text-slate-900 font-mono">{selectedUnit.dimensions}</strong>
                    </div>
                  )}
                  <div className="flex justify-between">
                    <span className="text-slate-500">Orientation:</span>
                    <strong className="text-slate-900">{selectedUnit.facing}</strong>
                  </div>
                </div>

                {/* Status Specific Information */}
                {selectedUnit.status === 'booked' && (
                  <div className="p-3 rounded-xl bg-slate-100 border border-slate-200 text-[11px] space-y-1">
                    <div className="font-bold text-slate-900">Allottee: {selectedUnit.buyer}</div>
                    <div className="text-slate-600">Assigned Broker: {selectedUnit.broker}</div>
                    <div className="text-emerald-700 font-mono">Commission Locked: {selectedUnit.cpCommission}</div>
                  </div>
                )}

                {selectedUnit.status === 'token_hold' && (
                  <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-[11px] space-y-1">
                    <div className="font-bold text-amber-900">{selectedUnit.buyer}</div>
                    <div className="text-amber-800">{selectedUnit.extraDetail}</div>
                    <div className="text-amber-900 font-mono">Broker: {selectedUnit.broker}</div>
                  </div>
                )}

                {selectedUnit.status === 'available' && (
                  <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-[11px] space-y-1">
                    <div className="font-bold text-emerald-900">100% Available for Immediate Allotment</div>
                    <div className="text-emerald-800">{selectedUnit.extraDetail}</div>
                    <div className="text-emerald-900 font-mono">Est. CP Payout: {selectedUnit.cpCommission}</div>
                  </div>
                )}
              </div>

              {/* Drawer Actions */}
              <div className="pt-2 space-y-2">
                {selectedUnit.status === 'available' && (
                  <button
                    onClick={() => handleHoldToken(selectedUnit.name)}
                    className="w-full py-3 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-sans font-bold text-xs shadow-warm-sm transition-all flex items-center justify-center gap-2"
                  >
                    <CheckCircle2 className="w-4 h-4" />
                    <span>Hold Unit for 48 Hours (₹51,000 Token)</span>
                  </button>
                )}

                <button
                  onClick={() => alert(`Brochure & Floorplan downloaded for ${selectedUnit.name}`)}
                  className="w-full py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-800 font-sans font-semibold text-xs border border-slate-200 transition-all flex items-center justify-center gap-2"
                >
                  <Download className="w-3.5 h-3.5 text-slate-600" />
                  <span>Download Stamped CAD Floorplan</span>
                </button>
              </div>
            </div>
          ) : (
            <div className="p-8 rounded-2xl bg-white border border-slate-200 shadow-warm-sm text-center flex flex-col items-center justify-center h-full min-h-[320px]">
              <div className="w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
                <Info className="w-6 h-6" />
              </div>
              <h5 className="font-serif font-bold text-slate-900 mb-1">Click Any Unit to Inspect</h5>
              <p className="text-xs text-slate-500 font-sans max-w-xs leading-relaxed">
                Select any plot, apartment duplex, or retail showroom from the grid to view live status, buyer KYC, and CP commission voucher.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Token Hold Toast Feedback */}
      {holdToast && (
        <div className="fixed bottom-6 right-6 z-50 bg-slate-900 text-white px-5 py-3 rounded-xl shadow-warm-lg flex items-center gap-3 font-sans text-xs border border-slate-700 animate-fadeIn">
          <Check className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{holdToast}</span>
        </div>
      )}
    </div>
  );
};
