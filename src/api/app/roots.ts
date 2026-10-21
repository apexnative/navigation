import type { LayoutRoot } from '../../interfaces/Layout';
import { buildRoot, type RootLayout, type UsesOfRoot } from '../layout/Root';

/** A root of the app: its layout, or a function making it from a param. */
export type RootValue = RootLayout | (() => RootLayout) | ((param: any) => RootLayout);

export type RootsMap = Record<string, RootValue>;

/** The screens a root of `roots` shows: those of its layout, or of the layout its function makes. */
export type UsesOfRootValue<Value> = Value extends (...args: any[]) => infer Layout
  ? UsesOfRoot<Layout>
  : UsesOfRoot<Value>;

/** The param a root takes: none for a layout or a function without one. */
export type RootParam<Value> = Value extends () => RootLayout
  ? []
  : Value extends (param: infer Param) => RootLayout
    ? [param: Param]
    : [];

/** A root to show: its name, or `[name, param]` for a root that takes a param. */
export type RootTarget<Roots extends RootsMap> = {
  [Name in keyof Roots & string]: RootParam<Roots[Name]> extends []
    ? Name
    : readonly [Name, ...RootParam<Roots[Name]>];
}[keyof Roots & string];

export function resolveRoot(
  roots: RootsMap | undefined,
  name: string,
  param: unknown[]
): LayoutRoot {
  const value = roots?.[name];
  if (value === undefined) {
    throw new Error(`[navigation] There is no root '${name}' in the roots of createNavigation`);
  }
  return buildRoot(
    typeof value === 'function' ? (value as (...args: unknown[]) => RootLayout)(...param) : value
  );
}
