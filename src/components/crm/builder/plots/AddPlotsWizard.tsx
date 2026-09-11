import React, { useState } from 'react';
import { 
  X, Layers, Plus, Upload, CheckCircle2, 
  Compass, FileSpreadsheet, RefreshCw 
} from 'lucide-react';
import { useCrm } from '../../../../context/CrmContext';
import { PlotFacing } from '../../../../types/crm';

interface AddPlotsWizardProps {
  onClose: () => void;
  onSuccess: () => void;
}

export const AddPlotsWizard: React.FC<AddPlotsWizardProps> = ({
  onClose,
  onSuccess,
}) => {
  const { activeProjectId, bulkAddPlots } = useCrm();

  const [mode, setMode] = useState<'BATCH' | 'CSV'>('BATCH');
  const [startNum, setStartNum] = useState(125);
  const [count, setCount] = useState(12);
  const [prefix, setPrefix] = useState('Plot #');
  const [sizeValue, setSizeValue] = useState(1800);
  const [pricePerSqft, setPricePerSqft] = useState(2200);
  const [defaultFacing, setDefaultFacing] = useState<PlotFacing>('E');
  const [isLoading, setIsLoading] = useState(false);

  const handleGenerate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeProjectId) return;
    setIsLoading(true);

    setTimeout(() => {
      const newPlots = [];
      const total = Number(count);
      const start = Number(startNum);

      for (let i = 0; i < total; i++) {
        const num = start + i;
        const size = Number(sizeValue);
        const rate = Number(pricePerSqft);
        const isCorner = i === 0 || i === total - 1;
        const isGarden = i % 4 === 0;

        newPlots.push({
          projectId: activeProjectId,
          plotNumber: `${prefix}${num}`,
          status: 'AVAILABLE' as const,
          sizeValue: size,
          sizeUnit: 'SQ_FT' as const,
          sizeSqft: size,
          facing: defaultFacing,
          price: size * rate,
          pricePerSqft: rate,
          isCorner,
          isGarden,
          isHot: isCorner,
          gridRow: Math.floor(i / 6) + 4,
          gridCol: i % 6,
        });
      }

      bulkAddPlots(newPlots);
      setIsLoading(false);
      onSuccess();
    }, 400);
  };

  return (
    <div className="fixed inset-0 z-50 bg-espresso-950/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl border border-sand-300 max-w-lg w-full p-6 sm:p-8 shadow-2xl animate-fadeIn text-left">
        
        {/* Header */}
        <div className="flex items-center justify-between pb-4 border-b border-sand-200">
          <div>
            <span className="text-[10px] font-mono text-forest uppercase tracking-wider font-bold">
              Inventory Wizard
            </span>
            <h2 className="font-serif font-bold text-2xl text-espresso-950">
              Bulk Create Plot Matrix
            </h2>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 rounded-xl hover:bg-sand-100 text-espresso-500"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Wizard Mode Selector */}
        <div className="flex gap-2 my-4 p-1 bg-sand-100 rounded-xl border border-sand-200 text-xs font-semibold">
          <button
            type="button"
            onClick={() => setMode('BATCH')}
            className={`flex-1 py-2 rounded-lg transition-all flex items-center justify-center gap-1.5 ${
              mode === 'BATCH' ? 'bg-white shadow-sm font-bold text-espresso-950' : 'text-espresso-600'
            }`}
          >
            <Layers className="w-3.5 h-3.5 text-forest" />
            <span>Automatic Batch Generator</span>
          </button>
          <button
            type="button"
            onClick={() => setMode('CSV')}
            className={`flex-1 py-2 rounded-lg transition-all flex items-center justify-center gap-1.5 ${
              mode === 'CSV' ? 'bg-white shadow-sm font-bold text-espresso-950' : 'text-espresso-600'
            }`}
          >
            <FileSpreadsheet className="w-3.5 h-3.5 text-forest" />
            <span>Excel / CSV Import</span>
          </button>
        </div>

        {mode === 'BATCH' ? (
          <form onSubmit={handleGenerate} className="space-y-4 text-xs font-sans">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Prefix (e.g. Plot #)
                </label>
                <input
                  type="text"
                  required
                  value={prefix}
                  onChange={(e) => setPrefix(e.target.value)}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Starting Plot Number
                </label>
                <input
                  type="number"
                  required
                  value={startNum}
                  onChange={(e) => setStartNum(Number(e.target.value))}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono outline-none"
                />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Total Plots to Add
                </label>
                <input
                  type="number"
                  required
                  min={1}
                  max={100}
                  value={count}
                  onChange={(e) => setCount(Number(e.target.value))}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Standard Size (Sq.ft)
                </label>
                <input
                  type="number"
                  required
                  value={sizeValue}
                  onChange={(e) => setSizeValue(Number(e.target.value))}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono outline-none"
                />
              </div>

              <div>
                <label className="block font-semibold text-espresso-800 mb-1">
                  Rate (₹ / Sq.ft)
                </label>
                <input
                  type="number"
                  required
                  value={pricePerSqft}
                  onChange={(e) => setPricePerSqft(Number(e.target.value))}
                  className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 focus:bg-white text-xs font-mono outline-none"
                />
              </div>
            </div>

            <div>
              <label className="block font-semibold text-espresso-800 mb-1">
                Default Cardinal Facing
              </label>
              <select
                value={defaultFacing}
                onChange={(e) => setDefaultFacing(e.target.value as any)}
                className="w-full px-3.5 py-2 rounded-xl border border-sand-300 bg-sand-50 text-xs font-sans outline-none"
              >
                <option value="E">East Facing (Vaastu Compliant)</option>
                <option value="NE">North-East Facing</option>
                <option value="N">North Facing</option>
                <option value="W">West Facing</option>
                <option value="S">South Facing</option>
              </select>
            </div>

            <div className="p-3 rounded-xl bg-sand-50 border border-sand-200 text-espresso-600 text-[11px] leading-relaxed">
              This will automatically provision <strong>{count} plots</strong> ({prefix}{startNum} to {prefix}{startNum + count - 1}), calculate total prices (₹{(sizeValue * pricePerSqft).toLocaleString('en-IN')}), and register them on the Cadastral Matrix.
            </div>

            <div className="pt-3 border-t border-sand-200 flex items-center justify-between gap-3">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 rounded-xl border border-sand-300 text-espresso-700 hover:bg-sand-100 font-semibold"
              >
                Cancel
              </button>

              <button
                type="submit"
                disabled={isLoading}
                className="px-5 py-2 rounded-xl bg-forest hover:bg-forest-light text-white font-bold uppercase tracking-wider shadow-warm-sm flex items-center gap-1.5"
              >
                {isLoading ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : 'Generate & Place on Grid'}
              </button>
            </div>
          </form>
        ) : (
          <div className="space-y-4 text-center py-6 text-xs">
            <div className="w-14 h-14 rounded-2xl bg-emerald-50 text-forest border border-emerald-200 flex items-center justify-center mx-auto">
              <FileSpreadsheet className="w-7 h-7" />
            </div>
            <div>
              <h4 className="font-bold text-sm text-espresso-950">
                Upload Masterplan Excel Spreadsheet
              </h4>
              <p className="text-espresso-500 mt-1 max-w-sm mx-auto">
                Upload your AutoCAD or survey demarcation file (.xlsx, .csv) with plot numbers, boundaries, and asking rates.
              </p>
            </div>

            <div className="p-6 rounded-2xl border-2 border-dashed border-sand-300 hover:border-forest/60 bg-sand-50/50 cursor-pointer">
              <Upload className="w-6 h-6 text-forest mx-auto mb-2" />
              <div className="font-bold text-espresso-900">Drag & Drop .XLSX / .CSV here</div>
              <div className="text-[10px] text-espresso-500 mt-0.5">Supports RERA-standard column format</div>
            </div>

            <button
              onClick={() => {
                setCount(24);
                setMode('BATCH');
              }}
              className="text-forest hover:underline font-semibold"
            >
              Or use Batch Generator instead →
            </button>
          </div>
        )}

      </div>
    </div>
  );
};
