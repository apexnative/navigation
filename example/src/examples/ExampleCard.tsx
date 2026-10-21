import React, { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { fonts, Raised, Tag, useTheme } from '../ui';

/**
 * An example on the Examples tab: a small picture of it, its name, what it shows and its topics, on a shadow of its
 * color.
 */
export function ExampleCard({
  title,
  description,
  tags,
  color,
  preview,
  onPress,
}: {
  title: string;
  description: string;
  tags: string[];
  color: string;
  preview: ReactNode;
  onPress: () => void;
}) {
  const t = useTheme();
  return (
    <Raised color={color} radius={22} style={styles.wrapper}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={title}
        onPress={onPress}
        style={({ pressed }) => [
          styles.card,
          { backgroundColor: pressed ? t.code : t.card, borderColor: t.border },
        ]}
      >
        {preview}
        <View style={styles.body}>
          <Text style={[styles.title, { color: t.text }]}>{title}</Text>
          <Text style={[styles.description, { color: t.textMuted }]}>{description}</Text>
          <View style={styles.tags}>
            {tags.map((tag) => (
              <Tag key={tag} small label={tag} />
            ))}
          </View>
        </View>
      </Pressable>
    </Raised>
  );
}

const styles = StyleSheet.create({
  wrapper: { marginBottom: 16 },
  card: {
    flexDirection: 'row',
    alignItems: 'stretch',
    gap: 14,
    borderRadius: 22,
    borderWidth: 1,
    padding: 12,
  },
  body: { flex: 1, gap: 6, paddingVertical: 4 },
  title: { fontFamily: fonts.display, fontSize: 20 },
  description: { fontFamily: fonts.body, fontSize: 13, lineHeight: 19 },
  tags: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 'auto', paddingTop: 6 },
});
