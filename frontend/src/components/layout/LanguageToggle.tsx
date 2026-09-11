import { useTranslation } from 'react-i18next';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { SUPPORTED_LANGUAGES, type SupportedLanguage } from '@/i18n';

export function LanguageToggle() {
  const { t, i18n } = useTranslation();

  return (
    <Select
      value={i18n.language}
      onValueChange={(value: SupportedLanguage) => i18n.changeLanguage(value)}
    >
      {/*
        w-28 alone was fine at M0/M1 with few TopBar siblings, but the right-
        side cluster (theme + language + notification bell + avatar) no
        longer fits a 360px viewport once the bell's unread badge hits two
        digits -- a realistic-data condition M5's own verification pass is
        what first surfaced (a nearly-empty dev org's badge always stayed
        single-digit). SelectValue already line-clamps to 1 line, so
        narrowing the trigger below sm: just clips the label text instead
        of overflowing the page.
      */}
      <SelectTrigger aria-label={t('language.label')} className="w-20 sm:w-28">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {SUPPORTED_LANGUAGES.map((lng) => (
          <SelectItem key={lng} value={lng}>
            {t(`language.${lng}`)}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
