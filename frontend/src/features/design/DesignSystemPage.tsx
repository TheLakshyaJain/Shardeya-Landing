import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Skeleton } from '@/components/ui/skeleton';
import { EmptyState } from '@/components/data/EmptyState';
import { ThemeToggle } from '@/components/layout/ThemeToggle';
import { LanguageToggle } from '@/components/layout/LanguageToggle';

// Token and variant names below (e.g. "primary", "outline", "sm") are code-level
// identifiers being documented, not prose — intentionally left untranslated, the
// same way a style guide wouldn't translate a CSS class name.
const colorTokens = [
  { name: 'background', className: 'bg-background' },
  { name: 'foreground', className: 'bg-foreground' },
  { name: 'card', className: 'bg-card' },
  { name: 'popover', className: 'bg-popover' },
  { name: 'primary', className: 'bg-primary' },
  { name: 'secondary', className: 'bg-secondary' },
  { name: 'muted', className: 'bg-muted' },
  { name: 'accent', className: 'bg-accent' },
  { name: 'destructive', className: 'bg-destructive' },
  { name: 'success', className: 'bg-success' },
  { name: 'warning', className: 'bg-warning' },
  { name: 'border', className: 'bg-border' },
] as const;

const buttonVariants = ['default', 'secondary', 'destructive', 'outline', 'ghost', 'link'] as const;
const buttonSizes = ['sm', 'default', 'lg', 'icon'] as const;
const badgeVariants = ['default', 'secondary', 'destructive', 'outline'] as const;

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold text-foreground">{title}</h2>
      {children}
    </section>
  );
}

export function DesignSystemPage() {
  const { t } = useTranslation();

  return (
    <div className="mx-auto max-w-4xl space-y-10 p-6">
      <header className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-foreground">{t('design.title')}</h1>
          <p className="text-sm text-muted-foreground">{t('design.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <ThemeToggle />
          <LanguageToggle />
        </div>
      </header>

      <Link to="/" className="text-sm text-primary underline-offset-4 hover:underline">
        ← {t('design.backHome')}
      </Link>

      <Section title={t('design.sections.colorTokens')}>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {colorTokens.map(({ name, className }) => (
            <div key={name} className="space-y-1">
              <div className={`h-12 rounded-md border border-border ${className}`} />
              <p className="text-xs text-muted-foreground">{name}</p>
            </div>
          ))}
        </div>
      </Section>

      <Section title={t('design.sections.typography')}>
        <div className="space-y-2">
          <p className="text-3xl font-semibold">{t('design.typographySamples.h3xl')}</p>
          <p className="text-2xl font-semibold">{t('design.typographySamples.h2xl')}</p>
          <p className="text-xl font-semibold">{t('design.typographySamples.hxl')}</p>
          <p className="text-lg font-medium">{t('design.typographySamples.hlg')}</p>
          <p className="text-base">{t('design.typographySamples.bodyBase')}</p>
          <p className="text-sm text-muted-foreground">{t('design.typographySamples.bodySmMuted')}</p>
          <p className="text-xs text-muted-foreground">{t('design.typographySamples.captionXs')}</p>
        </div>
      </Section>

      <Section title={t('design.sections.spacing')}>
        <div className="flex items-end gap-2">
          {[1, 2, 3, 4, 6, 8, 12, 16].map((n) => (
            <div key={n} className="flex flex-col items-center gap-1">
              <div className={`w-4 bg-primary`} style={{ height: `${n * 4}px` }} />
              <span className="text-xs text-muted-foreground">{n}</span>
            </div>
          ))}
        </div>
      </Section>

      <Section title={t('design.sections.buttons')}>
        <div className="flex flex-wrap gap-3">
          {buttonVariants.map((variant) => (
            <Button key={variant} variant={variant}>
              {variant}
            </Button>
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3">
          {buttonSizes.map((size) => (
            <Button key={size} size={size}>
              {size}
            </Button>
          ))}
        </div>
      </Section>

      <Section title={t('design.sections.badges')}>
        <div className="flex flex-wrap gap-2">
          {badgeVariants.map((variant) => (
            <Badge key={variant} variant={variant}>
              {variant}
            </Badge>
          ))}
        </div>
      </Section>

      <Section title={t('design.sections.inputs')}>
        <div className="grid gap-4 sm:grid-cols-2">
          <Input placeholder={t('design.input.textPlaceholder')} />
          <Select>
            <SelectTrigger>
              <SelectValue placeholder={t('design.input.selectPlaceholder')} />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="a">{t('design.input.optionA')}</SelectItem>
              <SelectItem value="b">{t('design.input.optionB')}</SelectItem>
            </SelectContent>
          </Select>
          <Textarea placeholder={t('design.input.textareaPlaceholder')} className="sm:col-span-2" />
        </div>
      </Section>

      <Section title={t('design.sections.dialog')}>
        <Dialog>
          <DialogTrigger asChild>
            <Button variant="outline">{t('design.dialog.trigger')}</Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{t('design.dialog.title')}</DialogTitle>
              <DialogDescription>{t('design.dialog.description')}</DialogDescription>
            </DialogHeader>
            <DialogFooter>
              <Button variant="secondary">{t('actions.cancel')}</Button>
              <Button>{t('actions.confirm')}</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </Section>

      <Section title={t('design.sections.toast')}>
        <div className="flex flex-wrap gap-3">
          <Button variant="outline" onClick={() => toast(t('design.toast.defaultMessage'))}>
            {t('design.toast.defaultLabel')}
          </Button>
          <Button variant="outline" onClick={() => toast.success(t('design.toast.successMessage'))}>
            {t('design.toast.successLabel')}
          </Button>
          <Button variant="outline" onClick={() => toast.error(t('design.toast.errorMessage'))}>
            {t('design.toast.errorLabel')}
          </Button>
        </div>
      </Section>

      <Section title={t('design.sections.card')}>
        <Card className="max-w-sm">
          <CardHeader>
            <CardTitle>{t('design.card.title')}</CardTitle>
            <CardDescription>{t('design.card.description')}</CardDescription>
          </CardHeader>
          <CardContent>
            <Badge variant="secondary">{t('design.card.status')}</Badge>
          </CardContent>
        </Card>
      </Section>

      <Section title={t('design.sections.table')}>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('design.table.plot')}</TableHead>
              <TableHead>{t('design.table.status')}</TableHead>
              <TableHead>{t('design.table.area')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow>
              <TableCell>A-101</TableCell>
              <TableCell>
                <Badge>{t('design.table.sold')}</Badge>
              </TableCell>
              <TableCell>1,200</TableCell>
            </TableRow>
            <TableRow>
              <TableCell>A-102</TableCell>
              <TableCell>
                <Badge variant="outline">{t('design.table.available')}</Badge>
              </TableCell>
              <TableCell>1,350</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </Section>

      <Section title={t('design.sections.emptyState')}>
        <EmptyState title={t('design.emptyState.title')} description={t('design.emptyState.description')} />
      </Section>

      <Section title={t('design.sections.skeleton')}>
        <div className="space-y-2">
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-24 w-full" />
        </div>
      </Section>
    </div>
  );
}
