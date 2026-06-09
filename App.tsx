import React from 'react';
import {
  StatusBar,
  StyleSheet,
  Text,
  View,
  useColorScheme,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';

function App(): React.JSX.Element {
  const isDarkMode = useColorScheme() === 'dark';
  const titleColor = isDarkMode ? '#f8fafc' : '#101828';
  const bodyColor = isDarkMode ? '#cbd5e1' : '#475467';
  const cardBackground = isDarkMode ? '#111c2f' : '#fffaf0';
  const cardBorder = isDarkMode ? '#243247' : '#ead7b3';
  const cardTitleColor = isDarkMode ? '#f8fafc' : '#7c2d12';
  const cardBodyColor = isDarkMode ? '#dbe4f0' : '#7a5535';

  return (
    <SafeAreaView style={[styles.screen, isDarkMode ? styles.screenDark : styles.screenLight]}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor={isDarkMode ? '#08111f' : '#f3efe6'}
      />
      <View style={styles.hero}>
        <Text style={styles.eyebrow}>Sculpt MVP</Text>
        <Text style={[styles.title, {color: titleColor}]}>
          Widget-first calorie tracking starts on Android.
        </Text>
        <Text style={[styles.body, {color: bodyColor}]}>
          The home-screen widget proof of concept is wired on the native side. Add the widget,
          then use "Refresh demo" to confirm local state updates are flowing.
        </Text>
      </View>

      <View style={[styles.card, {backgroundColor: cardBackground, borderColor: cardBorder}]}>
        <Text style={[styles.cardTitle, {color: cardTitleColor}]}>What is ready</Text>
        <Text style={[styles.cardBody, {color: cardBodyColor}]}>Native widget shell</Text>
        <Text style={[styles.cardBody, {color: cardBodyColor}]}>
          SharedPreferences-backed demo state
        </Text>
        <Text style={[styles.cardBody, {color: cardBodyColor}]}>
          React Native app shell for future setup and settings
        </Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    paddingHorizontal: 24,
    paddingVertical: 32,
  },
  screenDark: {
    backgroundColor: '#08111f',
  },
  screenLight: {
    backgroundColor: '#f3efe6',
  },
  hero: {
    marginTop: 24,
    gap: 12,
  },
  eyebrow: {
    color: '#d97706',
    fontSize: 14,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  title: {
    fontSize: 34,
    fontWeight: '800',
    lineHeight: 40,
  },
  body: {
    fontSize: 16,
    lineHeight: 24,
    maxWidth: 520,
  },
  card: {
    marginTop: 32,
    borderRadius: 24,
    borderWidth: 1,
    gap: 10,
    padding: 20,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '700',
  },
  cardBody: {
    fontSize: 15,
    lineHeight: 22,
  },
});

export default App;
