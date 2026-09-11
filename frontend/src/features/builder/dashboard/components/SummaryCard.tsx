import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatCompactIndianCurrency } from '@/lib/formatters';
import type { DashboardCard as DashboardCardData } from '../types';

interface SummaryCardProps {
  cardKey: string;
  card: DashboardCardData;
}

// B-01 §6: icon, label, big value, optional sub-value, tap -> filtered
// destination. Trend-vs-last-month is a documented future-scalability item
// (§13), not built this milestone -- one extra query per card for a
// comparison that isn't in the exit criteria wasn't worth the added load on
// a page every single user opens first.
//
// totalRevenue is the one card whose own `value` IS a currency amount
// (BigDecimal on the backend), not a count -- every other card's value is a
// plain integer, with `amount` (if present) as a separate currency
// sub-value. Rendering totalRevenue through the same plain toLocaleString()
// path as the count cards would show a raw un-abbreviated rupee figure
// instead of the required ₹18.24 Cr format (CLAUDE.md rule #2).
const AMOUNT_VALUED_CARDS = new Set(['totalRevenue']);

// Plot-count cards have no real filtered destination (plots live inside
// each project's own grid, there's no cross-project plot list page for
// `?plotStatus=` to land on), and Active Leads was asked to stay
// non-interactive too -- both left as plain, non-clickable cards rather
// than linking somewhere that doesn't actually do anything useful.
const NON_CLICKABLE_CARDS = new Set(['totalPlots', 'availablePlots', 'soldPlots', 'reservedPlots', 'activeLeads']);

export function SummaryCard({ cardKey, card }: SummaryCardProps) {
  const { t } = useTranslation('dashboard');
  const isAmountCard = AMOUNT_VALUED_CARDS.has(cardKey);
  const clickable = !NON_CLICKABLE_CARDS.has(cardKey);

  const body = (
    <Card className={`h-full${clickable ? ' transition-colors hover:bg-accent/50' : ''}`}>
      <CardHeader className="pb-1">
        <CardTitle className="text-xs font-medium text-muted-foreground">{t(`cards.${cardKey}`)}</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-xl font-semibold" title={String(card.value)}>
          {isAmountCard ? formatCompactIndianCurrency(card.value) : card.value.toLocaleString('en-IN')}
        </p>
        {!isAmountCard && card.amount != null && <p className="text-sm text-muted-foreground">{formatCompactIndianCurrency(card.amount)}</p>}
      </CardContent>
    </Card>
  );

  // A grid item stretches to the row's full height by default, but the
  // visible Card only ever filled its own content height -- for a
  // non-clickable card, Card itself IS the grid item, so it happened to
  // stretch correctly; a clickable card is wrapped in this Link, and
  // without h-full here (and on Card above) the stretched Link left
  // invisible empty space below a shorter Card, making cards in the same
  // row look uneven whenever their content heights differed (e.g. a card
  // with no `amount` sub-line next to one that has one).
  return clickable ? (
    <Link to={card.link} className="block h-full">
      {body}
    </Link>
  ) : (
    body
  );
}
