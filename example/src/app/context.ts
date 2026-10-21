/** The context of the Showcase: what every screen reads with `useAppContext`. */
export interface AppContext {
  /** The user who signed in, or null. */
  user: string | null;
}

export const initialContext: AppContext = { user: null };
