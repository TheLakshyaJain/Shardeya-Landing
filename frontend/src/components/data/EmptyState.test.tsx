import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EmptyState } from './EmptyState';

describe('EmptyState', () => {
  it('renders title and description', () => {
    render(<EmptyState title="No plots yet" description="Upload a plot list to get started." />);

    expect(screen.getByText('No plots yet')).toBeInTheDocument();
    expect(screen.getByText('Upload a plot list to get started.')).toBeInTheDocument();
  });

  it('renders without a description', () => {
    render(<EmptyState title="Nothing here" />);

    expect(screen.getByText('Nothing here')).toBeInTheDocument();
  });
});
