/** Types a text into a setter letter by letter, as a person would. */
export function typeInto(setText: (text: string) => void, text: string, delay = 70): Promise<void> {
  return new Promise((resolve) => {
    let index = 0;
    const next = () => {
      index += 1;
      setText(text.slice(0, index));
      if (index < text.length) setTimeout(next, delay);
      else resolve();
    };
    setTimeout(next, delay);
  });
}
