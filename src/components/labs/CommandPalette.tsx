import React, { useState, useEffect, useRef } from 'react';
import { 
  Search, Command, ArrowRight, Layers, Calculator, 
  MessageSquare, Globe, Volume2, X, Sparkles, Check 
} from 'lucide-react';
import { useLanguage } from '../../context/LanguageContext';
import { audioHaptics } from '../../utils/audioHaptics';

interface CommandPaletteProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectUnit?: (unitId: string) => void;
  onTriggerBlueprint?: () => void;
  onTriggerWhatsApp?: () => void;
}

interface CommandItem {
  id: string;
  category: 'Inventory' | 'Calculators' | 'Actions';
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  subtitle: string;
  badge?: string;
  action: () => void;
}

export const CommandPalette: React.FC<CommandPaletteProps> = ({
  isOpen,
  onClose,
  onSelectUnit,
  onTriggerBlueprint,
  onTriggerWhatsApp,
}) => {
  const { language, toggleLanguage } = useLanguage();
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [actionSuccessMsg, setActionSuccessMsg] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Focus input when modal opens
  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setSelectedIndex(0);
      setTimeout(() => inputRef.current?.focus(), 50);
      audioHaptics.playSwitch();
    }
  }, [isOpen]);

  // Global shortcut listener for ⌘K and Escape
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        if (isOpen) onClose();
        else {
          // Open trigger handled by parent or shortcut
        }
      }
      if (e.key === 'Escape' && isOpen) {
        onClose();
        audioHaptics.playClick();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const items: CommandItem[] = [
    {
      id: 'cmd-p104',
      category: 'Inventory',
      icon: Layers,
      title: 'Villa Plot #104 — Park Facing Corner',
      subtitle: '2,400 sq.ft • ₹2.15 Cr • Available for Token',
      badge: 'PLOT',
      action: () => {
        onSelectUnit?.('p4');
        triggerSuccess('Selected Villa Plot #104');
      },
    },
    {
      id: 'cmd-ab502',
      category: 'Inventory',
      icon: Layers,
      title: 'Apt B-502 — 4 BHK Luxury Duplex',
      subtitle: '3,100 sq.ft • ₹2.95 Cr • Booked to Rahul Singhal',
      badge: 'APARTMENT',
      action: () => {
        onSelectUnit?.('p5');
        triggerSuccess('Selected Apt B-502');
      },
    },
    {
      id: 'cmd-shop02',
      category: 'Inventory',
      icon: Layers,
      title: 'Commercial Shop C-02 — High Street Anchor',
      subtitle: '1,250 sq.ft • ₹1.45 Cr • Galleria 84',
      badge: 'RETAIL',
      action: () => {
        onSelectUnit?.('c2');
        triggerSuccess('Selected Shop C-02');
      },
    },
    {
      id: 'cmd-comm-calc',
      category: 'Calculators',
      icon: Calculator,
      title: 'Calculate Commission on ₹10 Cr Volume',
      subtitle: 'Instant CP payout voucher generation (Level 2: 1.5% = ₹15L)',
      badge: 'PAYOUT',
      action: () => {
        triggerSuccess('Generated ₹15L CP Voucher Calculation');
      },
    },
    {
      id: 'cmd-emi-calc',
      category: 'Calculators',
      icon: Calculator,
      title: 'Home Loan EMI Planner (₹75 Lakh @ 8.5%)',
      subtitle: 'Monthly EMI: ₹65,082 / month for 20 years',
      badge: 'EMI',
      action: () => {
        triggerSuccess('Calculated Home Loan EMI: ₹65,082/mo');
      },
    },
    {
      id: 'cmd-lang',
      category: 'Actions',
      icon: Globe,
      title: language === 'en' ? 'Switch Interface to हिन्दी' : 'Switch Interface to English',
      subtitle: 'Bilingual real estate workspace for on-site managers & CP teams',
      badge: 'BILINGUAL',
      action: () => {
        toggleLanguage();
        triggerSuccess(`Switched to ${language === 'en' ? 'हिन्दी' : 'English'}`);
      },
    },
    {
      id: 'cmd-blueprint',
      category: 'Actions',
      icon: Layers,
      title: 'Toggle Architectural Blueprint CAD Mode',
      subtitle: 'View surveyor dimensions, setback lines, and Vastu compass',
      badge: 'CAD VIEW',
      action: () => {
        onTriggerBlueprint?.();
        triggerSuccess('Activated Blueprint CAD Mode');
      },
    },
    {
      id: 'cmd-wa',
      category: 'Actions',
      icon: MessageSquare,
      title: 'Simulate Automated WhatsApp Site Visit Notice',
      subtitle: 'Send Google Maps pin & site manager transit card',
      badge: 'WHATSAPP',
      action: () => {
        onTriggerWhatsApp?.();
        triggerSuccess('WhatsApp Site Visit Notice Dispatched');
      },
    },
    {
      id: 'cmd-sound',
      category: 'Actions',
      icon: Volume2,
      title: audioHaptics.isMuted() ? 'Enable Tactile Sound Haptics' : 'Mute Tactile Sound Haptics',
      subtitle: 'Mechanical clicks on plot selection & slider movement',
      badge: 'HAPTICS',
      action: () => {
        audioHaptics.toggleMute();
        triggerSuccess(audioHaptics.isMuted() ? 'Sound Haptics Muted' : 'Sound Haptics Enabled');
      },
    },
  ];

  const filteredItems = items.filter(
    (item) =>
      item.title.toLowerCase().includes(query.toLowerCase()) ||
      item.subtitle.toLowerCase().includes(query.toLowerCase()) ||
      item.category.toLowerCase().includes(query.toLowerCase())
  );

  const triggerSuccess = (msg: string) => {
    audioHaptics.playSuccess();
    setActionSuccessMsg(msg);
    setTimeout(() => {
      setActionSuccessMsg(null);
      onClose();
    }, 1200);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      audioHaptics.playSnap();
      setSelectedIndex((prev) => (prev + 1) % (filteredItems.length || 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      audioHaptics.playSnap();
      setSelectedIndex((prev) => (prev - 1 + filteredItems.length) % (filteredItems.length || 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const item = filteredItems[selectedIndex];
      if (item) {
        item.action();
      }
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-20 sm:pt-28 px-4 bg-slate-950/60 backdrop-blur-md animate-fadeIn">
      <div 
        className="w-full max-w-2xl bg-white rounded-2xl border border-slate-200 shadow-2xl overflow-hidden text-left"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Search Header */}
        <div className="flex items-center px-4 py-3.5 border-b border-slate-200 gap-3">
          <Search className="w-5 h-5 text-slate-400 shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSelectedIndex(0);
            }}
            onKeyDown={handleKeyDown}
            placeholder="Search plots, units, calculators, or actions (↑↓ to navigate, ↵ to select)..."
            className="w-full text-sm font-sans text-slate-900 bg-transparent focus:outline-none placeholder:text-slate-400"
          />
          <div className="flex items-center gap-1.5 shrink-0">
            <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-600 border border-slate-300">
              ESC
            </span>
            <button
              onClick={() => {
                audioHaptics.playClick();
                onClose();
              }}
              className="p-1 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-700 transition-colors"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Action Success Toast Overlay */}
        {actionSuccessMsg ? (
          <div className="p-8 text-center bg-emerald-50 text-emerald-900 flex flex-col items-center justify-center gap-2 animate-fadeIn">
            <div className="w-10 h-10 rounded-full bg-emerald-600 text-white flex items-center justify-center">
              <Check className="w-5 h-5" />
            </div>
            <div className="font-serif font-bold text-lg">{actionSuccessMsg}</div>
            <div className="text-xs text-emerald-700 font-sans">Command executed successfully</div>
          </div>
        ) : (
          /* Results List */
          <div className="max-h-96 overflow-y-auto p-2 divide-y divide-slate-100">
            {filteredItems.length === 0 ? (
              <div className="py-12 text-center text-slate-500 text-xs font-sans">
                No matching plots, calculators, or actions found for "{query}".
              </div>
            ) : (
              filteredItems.map((item, idx) => {
                const isSelected = idx === selectedIndex;
                const Icon = item.icon;

                return (
                  <div
                    key={item.id}
                    onMouseEnter={() => setSelectedIndex(idx)}
                    onClick={() => {
                      audioHaptics.playClick();
                      item.action();
                    }}
                    className={`flex items-center justify-between p-3 rounded-xl cursor-pointer transition-all ${
                      isSelected
                        ? 'bg-slate-100 text-slate-950 shadow-warm-sm'
                        : 'text-slate-700 hover:bg-slate-50'
                    }`}
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div
                        className={`p-2 rounded-lg ${
                          isSelected ? 'bg-emerald-600 text-white' : 'bg-slate-200/70 text-slate-600'
                        }`}
                      >
                        <Icon className="w-4 h-4" />
                      </div>
                      <div className="min-w-0">
                        <div className="text-xs font-sans font-bold truncate text-slate-900">
                          {item.title}
                        </div>
                        <div className="text-[11px] text-slate-500 truncate font-sans">
                          {item.subtitle}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0 ml-3">
                      {item.badge && (
                        <span className="font-mono text-[9px] px-2 py-0.5 rounded bg-white text-slate-600 border border-slate-200 font-bold">
                          {item.badge}
                        </span>
                      )}
                      <ArrowRight
                        className={`w-3.5 h-3.5 transition-transform ${
                          isSelected ? 'text-emerald-700 translate-x-0.5' : 'text-slate-300 opacity-0'
                        }`}
                      />
                    </div>
                  </div>
                );
              })
            )}
          </div>
        )}

        {/* Footer Shortcut Bar */}
        <div className="bg-slate-50 px-4 py-2.5 border-t border-slate-200 flex items-center justify-between text-[11px] font-mono text-slate-500">
          <div className="flex items-center gap-3">
            <span>↑↓ Navigate</span>
            <span>↵ Select</span>
            <span>ESC Close</span>
          </div>
          <div className="flex items-center gap-1.5 text-emerald-800 font-bold">
            <Sparkles className="w-3.5 h-3.5 text-emerald-600" />
            <span>Spotlight Command Bar</span>
          </div>
        </div>
      </div>
    </div>
  );
};
