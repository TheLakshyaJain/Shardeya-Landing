import React, { useState, useEffect } from 'react';
import { Volume2, VolumeX } from 'lucide-react';
import { audioHaptics } from '../../utils/audioHaptics';

export const SoundHapticsToggle: React.FC<{ className?: string }> = ({ className = '' }) => {
  const [muted, setMuted] = useState<boolean>(true);

  useEffect(() => {
    setMuted(audioHaptics.isMuted());
  }, []);

  const handleToggle = () => {
    const isNowMuted = audioHaptics.toggleMute();
    setMuted(isNowMuted);
  };

  return (
    <button
      onClick={handleToggle}
      className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-lg border text-xs font-sans font-medium transition-all shadow-warm-sm ${
        !muted
          ? 'bg-emerald-50 border-emerald-300 text-emerald-800'
          : 'bg-white border-slate-200 text-slate-600 hover:text-slate-900 hover:bg-slate-50'
      } ${className}`}
      title={muted ? 'Enable tactile mechanical clicks' : 'Disable tactile sound'}
    >
      {!muted ? (
        <>
          <Volume2 className="w-3.5 h-3.5 text-emerald-600 animate-pulse" />
          <span className="font-mono text-[11px] font-bold">Haptics: ON</span>
        </>
      ) : (
        <>
          <VolumeX className="w-3.5 h-3.5 text-slate-400" />
          <span className="font-mono text-[11px]">Haptics: OFF</span>
        </>
      )}
    </button>
  );
};
