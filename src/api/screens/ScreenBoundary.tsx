import React, { Component, type ComponentType, type ErrorInfo, type ReactNode } from 'react';
import type { ScreenInfo } from './registerScreens';

/**
 * What `errorFallback` gets: the error, the screen, and `retry`, which renders the screen again. When the start failed,
 * the screen is the first root it shows in, and `retry` runs the start again from `willMount`.
 */
export interface ErrorFallbackProps {
  error: unknown;
  screen: ScreenInfo;
  retry: () => void;
}

interface Props {
  screen: ScreenInfo;
  fallback?: ComponentType<ErrorFallbackProps>;
  onError?: (error: unknown, componentStack: string | null | undefined) => void;
  children: ReactNode;
}

interface State {
  failed: boolean;
  error: unknown;
}

/**
 * Keeps an error of a screen inside it: the screen shows the fallback, or nothing, and the other screens go on. With
 * `appProvider` every screen is in one React tree, which an error would take down whole.
 */
export class ScreenBoundary extends Component<Props, State> {
  state: State = { failed: false, error: undefined };

  static getDerivedStateFromError(error: unknown): State {
    return { failed: true, error };
  }

  componentDidCatch(error: unknown, info: ErrorInfo) {
    this.props.onError?.(error, info.componentStack);
  }

  retry = () => {
    this.setState({ failed: false, error: undefined });
  };

  render() {
    if (!this.state.failed) return this.props.children;
    const Fallback = this.props.fallback;
    return Fallback ? (
      <Fallback error={this.state.error} screen={this.props.screen} retry={this.retry} />
    ) : null;
  }
}
