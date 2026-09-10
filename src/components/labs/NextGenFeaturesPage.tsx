import React, { useEffect, useState } from 'react';
import { 
  ArrowLeft, Sparkles, Command, Sliders, Layers, 
  Building2, Ruler, TrendingUp, CheckCircle2, ChevronDown, 
  HelpCircle, ExternalLink, ShieldCheck, ArrowRight
} from 'lucide-react';
import { BeforeAfterSlider } from './BeforeAfterSlider';
import { MultiAssetSwitcher } from './MultiAssetSwitcher';
import { ArchitecturalBlueprintView } from './ArchitecturalBlueprintView';
import { OperationalRoiSlider } from './OperationalRoiSlider';
import { SoundHapticsToggle } from './SoundHapticsToggle';
import { CommandPalette } from './CommandPalette';
import { audioHaptics } from '../../utils/audioHaptics';

interface NextGenFeaturesPageProps {
  onBack: () => void;
  onOpenDemo?: () => void;
}

export const NextGenFeaturesPage: React.FC<NextGenFeaturesPageProps> = ({ 
  onBack,
  onOpenDemo 
}) => {
  const [isCommandPaletteOpen, setIsCommandPaletteOpen] = useState(false);
  const [activeSection, setActiveSection] = useState<string>('before-after');

  // Track scrolling to update active anchor
  useEffect(() => {
    const handleScroll = () => {
      const sections = ['before-after', 'multi-asset', 'blueprint-cad', 'spotlight-search', 'capital-roi'];
      const scrollY = window.scrollY + 200;

      for (const id of sections) {
        const el = document.getElementById(id);
        if (el) {
          const top = el.offsetTop;
          const height = el.offsetHeight;
          if (scrollY >= top && scrollY < top + height) {
            setActiveSection(id);
            break;
          }
        }
      }
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const scrollToSection = (id: string) => {
    audioHaptics.playClick();
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const navItems = [
    { id: 'before-after', label: '1. Operational Split Wipe', icon: Sliders },
    { id: 'multi-asset', label: '2. Multi-Asset Switcher', icon: Building2 },
    { id: 'blueprint-cad', label: '3. CAD Blueprint Mode', icon: Ruler },
    { id: 'spotlight-search', label: '4. ⌘K Spotlight Engine', icon: Command },
    { id: 'capital-roi', label: '5. Working Capital ROI', icon: TrendingUp },
  ];

  return (
    <div className="min-h-screen bg-sand-100 text-espresso-950 font-sans selection:bg-forest/15 selection:text-forest">
      {/* Global Command Palette */}
      <CommandPalette 
        isOpen={isCommandPaletteOpen}
        onClose={() => setIsCommandPaletteOpen(false)}
        onTriggerBlueprint={() => scrollToSection('blueprint-cad')}
      />

      {/* Sticky Top Bar */}
      <header className="sticky top-0 z-40 bg-white/90 backdrop-blur-xl border-b border-sand-300 shadow-warm-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            
            {/* Back Button */}
            <div className="flex items-center gap-4">
              <button
                onClick={() => {
                  audioHaptics.playClick();
                  onBack();
                }}
                className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border border-sand-300 bg-sand-50 hover:bg-sand-200 text-espresso-800 text-xs font-bold transition-all shadow-warm-sm group"
              >
                <ArrowLeft className="w-3.5 h-3.5 text-forest group-hover:-translate-x-1 transition-transform" />
                <span>Back to Main Landing Page</span>
              </button>

              <div className="hidden sm:flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                <span className="text-xs font-mono font-bold text-espresso-900 uppercase tracking-wider">
                  Features Lab
                </span>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-sand-200 text-sand-800 font-mono font-bold border border-sand-300">
                  PREVIEW
                </span>
              </div>
            </div>

            {/* Quick Actions Right */}
            <div className="flex items-center gap-3">
              {/* Sound Haptics Toggle */}
              <SoundHapticsToggle />

              {/* ⌘K Trigger Button */}
              <button
                onClick={() => {
                  audioHaptics.playSnap();
                  setIsCommandPaletteOpen(true);
                }}
                className="hidden md:inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border border-sand-300 bg-sand-50 hover:bg-sand-200 text-espresso-700 hover:text-espresso-950 text-xs font-mono transition-all shadow-warm-sm"
                title="Launch Command Palette (⌘K or Ctrl+K)"
              >
                <Command className="w-3.5 h-3.5 text-forest" />
                <span>Spotlight</span>
                <kbd className="px-1.5 py-0.5 rounded bg-white text-[10px] font-bold text-espresso-800 border border-sand-300">
                  ⌘K
                </kbd>
              </button>

              {onOpenDemo && (
                <button
                  onClick={() => {
                    audioHaptics.playSuccess();
                    onOpenDemo();
                  }}
                  className="px-4 py-1.5 rounded-lg bg-forest hover:bg-forest-light text-white text-xs font-bold uppercase tracking-wider shadow-warm-sm transition-all"
                >
                  Request Pilot
                </button>
              )}
            </div>
          </div>
        </div>

        {/* Anchor Jump Bar */}
        <div className="bg-sand-50/90 border-t border-sand-200 overflow-x-auto scrollbar-none">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 flex items-center gap-1 py-1.5 min-w-max">
            <span className="text-[11px] font-mono font-bold text-sand-800 uppercase tracking-wider mr-2">
              Jump To Feature:
            </span>
            {navItems.map((item) => {
              const Icon = item.icon;
              const isActive = activeSection === item.id;
              return (
                <button
                  key={item.id}
                  onClick={() => scrollToSection(item.id)}
                  className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-sans transition-all ${
                    isActive
                      ? 'bg-forest text-white font-bold shadow-warm-sm'
                      : 'text-espresso-700 hover:text-espresso-950 hover:bg-sand-200/80 font-medium'
                  }`}
                >
                  <Icon className="w-3 h-3" />
                  <span>{item.label}</span>
                </button>
              );
            })}
          </div>
        </div>
      </header>

      {/* Hero Explainer Header */}
      <section className="relative py-16 px-4 sm:px-6 lg:px-8 bg-ambient-luminous border-b border-sand-300 text-center">
        <div className="max-w-4xl mx-auto">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-forest/10 border border-forest/20 text-forest text-xs font-mono font-bold mb-4">
            <Sparkles className="w-3.5 h-3.5" />
            <span>PRE-INTEGRATION LAB STAGING</span>
          </div>

          <h1 className="text-3xl sm:text-4xl lg:text-5xl font-serif text-espresso-950 tracking-tight leading-tight">
            Next-Gen Real Estate Interaction Lab
          </h1>

          <p className="mt-4 text-base sm:text-lg text-espresso-700 leading-relaxed max-w-2xl mx-auto">
            Review 5 tactile, interactive landing page modules designed specifically for residential developers and broker syndicates. 
            No marketing jargon or fake AI chat boxes—every module below is built with real-world Indian statutory mechanics, Web Audio physical haptics, and live tactile calculations.
          </p>

          <div className="mt-6 flex flex-wrap items-center justify-center gap-4 text-xs font-mono text-sand-800">
            <span className="flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 text-forest" />
              <span>Zero Artificial AI Buzzwords</span>
            </span>
            <span className="text-sand-400">•</span>
            <span className="flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 text-forest" />
              <span>Web Audio Physical Haptics</span>
            </span>
            <span className="text-sand-400">•</span>
            <span className="flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 text-forest" />
              <span>Tested for High-Conversion Landing Pages</span>
            </span>
          </div>
        </div>
      </section>

      {/* Feature 1: Operational Split-Screen Wipe */}
      <section id="before-after" className="py-20 px-4 sm:px-6 lg:px-8 border-b border-sand-300">
        <div className="max-w-6xl mx-auto">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-8">
            <div>
              <div className="flex items-center gap-2 text-xs font-mono text-forest font-bold uppercase tracking-wider mb-1">
                <span>Module 01</span>
                <span>•</span>
                <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-900 border border-emerald-300">
                  Ready for Landing Page
                </span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-serif text-espresso-950 font-bold">
                Interactive Operational Wipe: Chaos vs Clarity
              </h2>
              <p className="text-sm text-espresso-700 mt-1 max-w-2xl">
                Prospective developers instantly recognize the nightmare of fragmented WhatsApp slips and corrupt Excel sheets. 
                Drag the slider to physically reveal Shardeya’s single source of truth.
              </p>
            </div>

            <div className="shrink-0">
              <span className="text-xs font-mono text-sand-800 bg-sand-200/80 px-3 py-1 rounded border border-sand-300">
                ↔ Drag handle to wipe
              </span>
            </div>
          </div>

          <BeforeAfterSlider />
        </div>
      </section>

      {/* Feature 2: Multi-Asset Class Switcher */}
      <section id="multi-asset" className="py-20 px-4 sm:px-6 lg:px-8 border-b border-sand-300 bg-ambient-warm">
        <div className="max-w-6xl mx-auto">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-8">
            <div>
              <div className="flex items-center gap-2 text-xs font-mono text-amber-700 font-bold uppercase tracking-wider mb-1">
                <span>Module 02</span>
                <span>•</span>
                <span className="px-2 py-0.5 rounded bg-amber-100 text-amber-900 border border-amber-300">
                  Ready for Landing Page
                </span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-serif text-espresso-950 font-bold">
                Multi-Asset Class Engine: Plots, High-Rise & Retail
              </h2>
              <p className="text-sm text-espresso-700 mt-1 max-w-2xl">
                Most platforms only support basic residential apartments. Shardeya adapts instantly to Plotted Townships (sq.yds / road frontage), Luxury High-Rises (tower/floor matrix), and Commercial High-Street Arcades (lockable retail & CAM).
              </p>
            </div>

            <div className="shrink-0">
              <span className="text-xs font-mono text-sand-800 bg-sand-200/80 px-3 py-1 rounded border border-sand-300">
                3 Supported Asset Classes
              </span>
            </div>
          </div>

          <MultiAssetSwitcher />
        </div>
      </section>

      {/* Feature 3: Architectural CAD Blueprint View */}
      <section id="blueprint-cad" className="py-20 px-4 sm:px-6 lg:px-8 border-b border-sand-300">
        <div className="max-w-6xl mx-auto">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-8">
            <div>
              <div className="flex items-center gap-2 text-xs font-mono text-forest font-bold uppercase tracking-wider mb-1">
                <span>Module 03</span>
                <span>•</span>
                <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-900 border border-emerald-300">
                  Ready for Landing Page
                </span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-serif text-espresso-950 font-bold">
                Architectural Blueprint Mode: Technical Dimensioning & Vastu
              </h2>
              <p className="text-sm text-espresso-700 mt-1 max-w-2xl">
                Builders, chief engineers, and discerning buyers demand exact technical specifications: front/rear setbacks, road frontage widths, soil bearing capacity (SBC), and 8-point Vastu alignment.
              </p>
            </div>

            <div className="shrink-0">
              <span className="text-xs font-mono text-sand-800 bg-sand-200/80 px-3 py-1 rounded border border-sand-300">
                Toggle CAD / Sales Mode
              </span>
            </div>
          </div>

          <ArchitecturalBlueprintView />
        </div>
      </section>

      {/* Feature 4: Spotlight Command Palette Explainer */}
      <section id="spotlight-search" className="py-20 px-4 sm:px-6 lg:px-8 border-b border-sand-300 bg-sand-50">
        <div className="max-w-6xl mx-auto">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-8">
            <div>
              <div className="flex items-center gap-2 text-xs font-mono text-forest font-bold uppercase tracking-wider mb-1">
                <span>Module 04</span>
                <span>•</span>
                <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-900 border border-emerald-300">
                  Ready for Landing Page
                </span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-serif text-espresso-950 font-bold">
                Spotlight Action Palette (⌘K / Ctrl+K)
              </h2>
              <p className="text-sm text-espresso-700 mt-1 max-w-2xl">
                Power users (sales directors, top channel partners) operate at high velocity. Pressing <kbd className="px-1.5 py-0.5 rounded bg-sand-200 text-espresso-900 font-mono text-xs border border-sand-300">⌘K</kbd> lets them jump directly to any plot dossier, calculate registration stamps, or issue WhatsApp gate passes in seconds.
              </p>
            </div>

            <div className="shrink-0">
              <button
                onClick={() => {
                  audioHaptics.playSnap();
                  setIsCommandPaletteOpen(true);
                }}
                className="px-4 py-2 rounded-lg bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-sm transition-all flex items-center gap-2"
              >
                <Command className="w-3.5 h-3.5" />
                <span>Launch ⌘K Palette Live</span>
              </button>
            </div>
          </div>

          {/* Static Preview Card showing keyboard capabilities */}
          <div className="rounded-2xl border border-sand-300 bg-white p-6 sm:p-8 shadow-warm-md">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              
              <div className="p-5 rounded-xl bg-sand-50 border border-sand-200">
                <div className="w-8 h-8 rounded-lg bg-forest/10 flex items-center justify-center text-forest mb-3">
                  <Command className="w-4 h-4" />
                </div>
                <h4 className="font-serif font-bold text-base text-espresso-950 mb-1">Instant Unit Lookup</h4>
                <p className="text-xs text-espresso-700 leading-relaxed mb-3">
                  Type <span className="font-mono font-bold">#104</span> or <span className="font-mono font-bold">3BHK</span> to jump directly to unit pricing, holding status, and allotment dossiers.
                </p>
                <div className="font-mono text-[11px] text-forest bg-forest/5 px-2 py-1 rounded border border-forest/15">
                  Try typing: "Villa", "Corner", "B-502"
                </div>
              </div>

              <div className="p-5 rounded-xl bg-sand-50 border border-sand-200">
                <div className="w-8 h-8 rounded-lg bg-amber-500/10 flex items-center justify-center text-amber-700 mb-3">
                  <Sliders className="w-4 h-4" />
                </div>
                <h4 className="font-serif font-bold text-base text-espresso-950 mb-1">One-Key Statutory Calculators</h4>
                <p className="text-xs text-espresso-700 leading-relaxed mb-3">
                  Instantly open Stamp Duty breakdown, Loan Amortization schedule, or Broker Commission slabs with a single keystroke.
                </p>
                <div className="font-mono text-[11px] text-amber-800 bg-amber-500/5 px-2 py-1 rounded border border-amber-500/20">
                  Try typing: "Stamp", "EMI", "Commission"
                </div>
              </div>

              <div className="p-5 rounded-xl bg-sand-50 border border-sand-200">
                <div className="w-8 h-8 rounded-lg bg-emerald-500/10 flex items-center justify-center text-emerald-700 mb-3">
                  <ShieldCheck className="w-4 h-4" />
                </div>
                <h4 className="font-serif font-bold text-base text-espresso-950 mb-1">Tactile Haptics Feedback</h4>
                <p className="text-xs text-espresso-700 leading-relaxed mb-3">
                  Synthesized Web Audio clicks and snaps make every navigation, switch, and selection feel like precision aerospace hardware.
                </p>
                <div className="font-mono text-[11px] text-emerald-800 bg-emerald-500/5 px-2 py-1 rounded border border-emerald-500/20">
                  Click 'Haptics' toggle top-right to listen
                </div>
              </div>

            </div>

            <div className="mt-6 pt-6 border-t border-sand-200 flex flex-wrap items-center justify-between gap-4">
              <div className="text-xs text-espresso-700 font-sans">
                💡 <span className="font-bold">Keyboard Pro-Tip:</span> On any screen in Shardeya, press <kbd className="px-1.5 py-0.5 rounded bg-sand-200 font-mono text-[11px] text-espresso-900 border border-sand-300">⌘K</kbd> (Mac) or <kbd className="px-1.5 py-0.5 rounded bg-sand-200 font-mono text-[11px] text-espresso-900 border border-sand-300">Ctrl+K</kbd> (Windows) to summon the spotlight.
              </div>
              <button
                onClick={() => {
                  audioHaptics.playSnap();
                  setIsCommandPaletteOpen(true);
                }}
                className="text-xs font-mono font-bold text-forest hover:text-forest-light flex items-center gap-1.5"
              >
                <span>Test Keyboard Navigation</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </button>
            </div>
          </div>
        </div>
      </section>

      {/* Feature 5: Operational ROI & Working Capital Yield */}
      <section id="capital-roi" className="py-20 px-4 sm:px-6 lg:px-8 border-b border-sand-300">
        <div className="max-w-6xl mx-auto">
          <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-4 mb-8">
            <div>
              <div className="flex items-center gap-2 text-xs font-mono text-emerald-700 font-bold uppercase tracking-wider mb-1">
                <span>Module 05</span>
                <span>•</span>
                <span className="px-2 py-0.5 rounded bg-emerald-100 text-emerald-900 border border-emerald-300">
                  Ready for Landing Page
                </span>
              </div>
              <h2 className="text-2xl sm:text-3xl font-serif text-espresso-950 font-bold">
                Operational ROI & Working Capital Acceleration
              </h2>
              <p className="text-sm text-espresso-700 mt-1 max-w-2xl">
                Demonstrates financial value to Managing Directors & CFOs. Shows exact labor hours saved, reconciliation disputes prevented, and bank interest saved from 14-day faster buyer collection realization.
              </p>
            </div>

            <div className="shrink-0">
              <span className="text-xs font-mono text-sand-800 bg-sand-200/80 px-3 py-1 rounded border border-sand-300">
                Reactive Financial Model
              </span>
            </div>
          </div>

          <OperationalRoiSlider />
        </div>
      </section>

      {/* Graduation Review Footer Call-to-Action */}
      <section className="py-20 px-4 sm:px-6 lg:px-8 bg-espresso-950 text-white text-center relative overflow-hidden">
        <div className="max-w-3xl mx-auto relative z-10">
          <div className="w-12 h-12 rounded-xl bg-forest flex items-center justify-center text-white mx-auto mb-5 shadow-warm-md">
            <Sparkles className="w-6 h-6" />
          </div>

          <h3 className="text-2xl sm:text-3xl lg:text-4xl font-serif font-bold tracking-tight">
            Ready to Graduate to the Main Landing Page?
          </h3>

          <p className="mt-4 text-sm sm:text-base text-sand-300 leading-relaxed max-w-xl mx-auto">
            You have now inspected all 5 next-generation interactions. You can approve which modules you'd like to place directly onto the live Shardeya landing page.
          </p>

          <div className="mt-8 flex flex-wrap items-center justify-center gap-4">
            <button
              onClick={() => {
                audioHaptics.playClick();
                onBack();
              }}
              className="px-6 py-3 rounded-xl bg-forest hover:bg-forest-light text-white font-sans font-bold text-xs uppercase tracking-wider shadow-warm-md transition-all flex items-center gap-2"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>Return to Main Landing Page</span>
            </button>

            {onOpenDemo && (
              <button
                onClick={() => {
                  audioHaptics.playSuccess();
                  onOpenDemo();
                }}
                className="px-6 py-3 rounded-xl border border-sand-500 bg-white/10 hover:bg-white/20 text-white font-sans font-bold text-xs uppercase tracking-wider transition-all"
              >
                Schedule Private Pilot
              </button>
            )}
          </div>
        </div>
      </section>
    </div>
  );
};
