import React from 'react';

interface GlassCardProps {
  children: React.ReactNode;
  className?: string;
  variant?: 'default' | 'emerald' | 'gold' | 'cyan';
  hoverEffect?: boolean;
  onClick?: () => void;
}

export const GlassCard: React.FC<GlassCardProps> = ({
  children,
  className = '',
  variant = 'default',
  hoverEffect = true,
  onClick,
}) => {
  const getVariantStyles = () => {
    switch (variant) {
      case 'emerald':
        return 'border-emerald-200/80 bg-white shadow-warm-sm hover:border-emerald-400 hover:shadow-warm-md';
      case 'gold':
        return 'border-amber-200/80 bg-white shadow-warm-sm hover:border-amber-400 hover:shadow-warm-md';
      case 'cyan':
        return 'border-slate-200 bg-white shadow-warm-sm hover:border-slate-300 hover:shadow-warm-md';
      default:
        return 'border-slate-200 bg-white shadow-warm-sm hover:border-slate-300 hover:shadow-warm-md';
    }
  };

  return (
    <div
      onClick={onClick}
      className={`relative rounded-2xl border transition-all duration-200 ${hoverEffect ? 'hover:-translate-y-1' : ''} ${getVariantStyles()} ${className}`}
    >
      <div className="relative z-10 w-full h-full">
        {children}
      </div>
    </div>
  );
};
