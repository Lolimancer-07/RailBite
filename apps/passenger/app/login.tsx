import React, { useEffect, useRef, useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useRouter } from "expo-router";
import { useSessionStore } from "../src/store/session.js";
import { colors } from "../src/ui/theme.js";

type Step = "phone" | "otp";

const OTP_LENGTH = 6;
const OTP_TTL_MS = 5 * 60 * 1000;
const RESEND_SECONDS = 30;
const MAX_ATTEMPTS = 3;

/** Demo-only OTP. Swap this for a real SMS provider call when wiring production auth. */
function generateOtp(): string {
  return String(Math.floor(100000 + Math.random() * 900000));
}

function formatPhone(digits: string): string {
  if (digits.length <= 5) return digits;
  return `${digits.slice(0, 5)} ${digits.slice(5)}`;
}

function formatCountdown(seconds: number): string {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins}:${String(secs).padStart(2, "0")}`;
}

export default function LoginScreen() {
  const router = useRouter();
  const setPhone = useSessionStore((s) => s.setPhone);

  const [step, setStep] = useState<Step>("phone");
  const [phoneDigits, setPhoneDigits] = useState("");
  const [phoneFocused, setPhoneFocused] = useState(false);
  const [enteredOtp, setEnteredOtp] = useState("");
  const [activeOtp, setActiveOtp] = useState("");
  const [otpExpiresAt, setOtpExpiresAt] = useState(0);
  const [resendIn, setResendIn] = useState(0);
  const [attempts, setAttempts] = useState(0);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const otpInputRef = useRef<TextInput>(null);

  const fullPhone = `+91 ${formatPhone(phoneDigits)}`;
  const locked = attempts >= MAX_ATTEMPTS;

  // Resend countdown
  useEffect(() => {
    if (step !== "otp" || resendIn <= 0) return;
    const timer = setTimeout(() => setResendIn((s) => Math.max(0, s - 1)), 1000);
    return () => clearTimeout(timer);
  }, [step, resendIn]);

  // Focus the OTP field as soon as the step opens
  useEffect(() => {
    if (step !== "otp") return;
    const timer = setTimeout(() => otpInputRef.current?.focus(), 250);
    return () => clearTimeout(timer);
  }, [step]);

  const handlePhoneChange = (text: string) => {
    let digits = text.replace(/\D/g, "");
    // Accept a pasted +91-prefixed number as well
    if (digits.length > 10 && digits.startsWith("91")) digits = digits.slice(2);
    setPhoneDigits(digits.slice(0, 10));
    setError("");
  };

  const issueOtp = (message: string) => {
    const code = generateOtp();
    setActiveOtp(code);
    setOtpExpiresAt(Date.now() + OTP_TTL_MS);
    setEnteredOtp("");
    setAttempts(0);
    setResendIn(RESEND_SECONDS);
    setError("");
    setNotice(message);
    // Visible in Metro logs for demos where the on-screen card is missed
    console.log(`[RailBite demo OTP] ${fullPhone} -> ${code}`);
  };

  const sendOtp = () => {
    if (phoneDigits.length !== 10) {
      setError("Enter your 10-digit mobile number.");
      return;
    }
    if (!/^[6-9]/.test(phoneDigits)) {
      setError("Indian mobile numbers start with 6, 7, 8 or 9.");
      return;
    }
    issueOtp("OTP generated. Check the demo card below.");
    setStep("otp");
  };

  const resendOtp = () => {
    if (resendIn > 0) return;
    issueOtp("A new OTP was generated.");
    otpInputRef.current?.focus();
  };

  const changeNumber = () => {
    setStep("phone");
    setEnteredOtp("");
    setError("");
    setNotice("");
  };

  const verifyOtp = () => {
    if (enteredOtp.length !== OTP_LENGTH) {
      setError(`Enter the ${OTP_LENGTH}-digit OTP.`);
      return;
    }
    if (Date.now() > otpExpiresAt) {
      setError("This OTP has expired. Tap Resend OTP for a new one.");
      return;
    }
    if (enteredOtp !== activeOtp) {
      const next = attempts + 1;
      setAttempts(next);
      setEnteredOtp("");
      setNotice("");
      if (next >= MAX_ATTEMPTS) {
        setError("Too many incorrect attempts. Tap Resend OTP for a new code.");
      } else {
        const left = MAX_ATTEMPTS - next;
        setError(`Incorrect OTP. ${left} attempt${left === 1 ? "" : "s"} left.`);
      }
      return;
    }

    setPhone(`+91${phoneDigits}`);
    const hasSeat = Boolean(useSessionStore.getState().session?.pnr);
    router.replace(hasSeat ? "/(tabs)/home" : "/onboarding");
  };

  const handleOtpChange = (text: string) => {
    setEnteredOtp(text.replace(/\D/g, "").slice(0, OTP_LENGTH));
    setError("");
  };

  const autofillOtp = () => {
    setEnteredOtp(activeOtp);
    setError("");
    otpInputRef.current?.focus();
  };

  return (
    <KeyboardAvoidingView
      style={styles.screen}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <ScrollView
        style={styles.screen}
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.hero}>
          <View style={styles.brandRow}>
            <View style={styles.logoBadge}>
              <Text style={styles.logoEmoji}>🍛</Text>
            </View>
            <View>
              <Text style={styles.brand}>RailBite</Text>
              <Text style={styles.brandSub}>ON-TRAIN DELIVERY</Text>
            </View>
          </View>
          <Text style={styles.heroTitle}>Hot food,{"\n"}right to your berth.</Text>
          <Text style={styles.heroSub}>Order from your seat. Delivered in about 10 minutes.</Text>
        </View>

        <View style={styles.sheet}>
          {step === "phone" ? (
            <>
              <Text style={styles.title}>Log in or sign up</Text>
              <Text style={styles.subtitle}>
                Enter your mobile number. We&apos;ll verify it with a 6-digit OTP.
              </Text>

              <Text style={styles.label}>Mobile number</Text>
              <View
                style={[
                  styles.phoneRow,
                  phoneFocused && styles.phoneRowFocused,
                  error ? styles.phoneRowError : null,
                ]}
              >
                <Text style={styles.countryCode}>+91</Text>
                <View style={styles.ccDivider} />
                <TextInput
                  style={styles.phoneInput}
                  value={formatPhone(phoneDigits)}
                  onChangeText={handlePhoneChange}
                  onFocus={() => setPhoneFocused(true)}
                  onBlur={() => setPhoneFocused(false)}
                  placeholder="98765 43210"
                  placeholderTextColor={colors.inkSoft}
                  keyboardType="number-pad"
                  returnKeyType="done"
                  onSubmitEditing={sendOtp}
                />
              </View>

              {error ? (
                <View style={styles.errorBox}>
                  <Text style={styles.errorText}>{error}</Text>
                </View>
              ) : (
                <Text style={styles.hint}>Indian mobile numbers only. No SMS is sent in demo mode.</Text>
              )}

              <TouchableOpacity
                style={styles.primaryBtn}
                activeOpacity={0.9}
                onPress={sendOtp}
              >
                <Text style={styles.primaryBtnText}>Send OTP →</Text>
              </TouchableOpacity>

              <Text style={styles.terms}>
                By continuing you agree to our Terms of Service and Privacy Policy.
              </Text>
            </>
          ) : (
            <>
              <TouchableOpacity onPress={changeNumber} style={styles.changeBtn} activeOpacity={0.7}>
                <Text style={styles.changeText}>‹ Change number</Text>
              </TouchableOpacity>

              <Text style={styles.title}>Verify your number</Text>
              <Text style={styles.subtitle}>
                Enter the 6-digit code generated for{" "}
                <Text style={styles.phoneStrong}>{fullPhone}</Text>.
              </Text>

              <View style={styles.otpRow}>
                {Array.from({ length: OTP_LENGTH }).map((_, index) => {
                  const digit = enteredOtp[index];
                  const isActive = index === enteredOtp.length;
                  return (
                    <View
                      key={index}
                      style={[
                        styles.otpCell,
                        digit ? styles.otpCellFilled : null,
                        isActive ? styles.otpCellActive : null,
                      ]}
                    >
                      <Text style={styles.otpDigit}>{digit ?? ""}</Text>
                    </View>
                  );
                })}
                {/* Transparent input sits on top so tapping any cell opens the keypad */}
                <TextInput
                  ref={otpInputRef}
                  style={styles.otpHiddenInput}
                  value={enteredOtp}
                  onChangeText={handleOtpChange}
                  keyboardType="number-pad"
                  maxLength={OTP_LENGTH}
                  caretHidden
                  accessibilityLabel="One-time password"
                />
              </View>

              {error ? (
                <View style={styles.errorBox}>
                  <Text style={styles.errorText}>{error}</Text>
                </View>
              ) : notice ? (
                <Text style={styles.noticeText}>{notice}</Text>
              ) : null}

              <View style={styles.demoCard}>
                <View style={styles.demoTop}>
                  <Text style={styles.demoBadge}>DEMO MODE</Text>
                  <Text style={styles.demoModeText}>No SMS is sent</Text>
                </View>
                <Text style={styles.demoText}>
                  Your one-time code for <Text style={styles.demoPhone}>{fullPhone}</Text> is
                </Text>
                <View style={styles.demoCodeRow}>
                  <Text style={styles.demoCode}>{activeOtp}</Text>
                  <TouchableOpacity style={styles.autofillBtn} onPress={autofillOtp} activeOpacity={0.8}>
                    <Text style={styles.autofillText}>Autofill</Text>
                  </TouchableOpacity>
                </View>
                <Text style={styles.demoFoot}>
                  In production this code arrives by SMS. For this demo it is shown here and in the
                  Metro console.
                </Text>
              </View>

              <TouchableOpacity
                style={[styles.primaryBtn, locked && styles.primaryBtnDisabled]}
                activeOpacity={0.9}
                onPress={verifyOtp}
                disabled={locked}
              >
                <Text style={styles.primaryBtnText}>Verify &amp; continue</Text>
              </TouchableOpacity>

              <View style={styles.resendRow}>
                <Text style={styles.resendText}>Didn&apos;t get the code?</Text>
                <TouchableOpacity onPress={resendOtp} disabled={resendIn > 0} activeOpacity={0.7}>
                  <Text style={[styles.resendAction, resendIn > 0 && styles.resendDisabled]}>
                    {resendIn > 0 ? `Resend in ${formatCountdown(resendIn)}` : "Resend OTP"}
                  </Text>
                </TouchableOpacity>
              </View>
            </>
          )}
        </View>

        <View style={styles.footer}>
          <Text style={styles.footerItem}>🔒 OTP protected</Text>
          <Text style={styles.footerDot}>•</Text>
          <Text style={styles.footerItem}>🚆 Seat-level delivery</Text>
          <Text style={styles.footerDot}>•</Text>
          <Text style={styles.footerItem}>💵 Pay on delivery</Text>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.bg,
  },
  scrollContent: {
    paddingBottom: 32,
  },
  hero: {
    backgroundColor: colors.primary,
    paddingHorizontal: 24,
    paddingTop: 26,
    paddingBottom: 58,
    borderBottomLeftRadius: 30,
    borderBottomRightRadius: 30,
  },
  brandRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    marginBottom: 26,
  },
  logoBadge: {
    width: 46,
    height: 46,
    borderRadius: 15,
    backgroundColor: "rgba(255,255,255,0.16)",
    alignItems: "center",
    justifyContent: "center",
  },
  logoEmoji: {
    fontSize: 24,
  },
  brand: {
    color: "#ffffff",
    fontSize: 22,
    fontWeight: "800",
    letterSpacing: 0.3,
  },
  brandSub: {
    color: "#b7f0d0",
    fontSize: 9,
    fontWeight: "700",
    letterSpacing: 1.6,
    marginTop: 2,
  },
  heroTitle: {
    color: "#ffffff",
    fontSize: 30,
    fontWeight: "800",
    lineHeight: 36,
  },
  heroSub: {
    color: "#d1fae5",
    fontSize: 13,
    lineHeight: 19,
    marginTop: 10,
    maxWidth: 280,
  },
  sheet: {
    backgroundColor: "#ffffff",
    marginHorizontal: 16,
    marginTop: -34,
    borderRadius: 22,
    padding: 20,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: "#0b1f14",
    shadowOpacity: 0.08,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: 8 },
    elevation: 4,
  },
  title: {
    fontSize: 22,
    fontWeight: "800",
    color: colors.ink,
  },
  subtitle: {
    fontSize: 13,
    color: colors.inkSoft,
    lineHeight: 19,
    marginTop: 6,
    marginBottom: 20,
  },
  label: {
    fontSize: 12,
    fontWeight: "700",
    color: colors.ink,
    marginBottom: 8,
    textTransform: "uppercase",
    letterSpacing: 0.6,
  },
  phoneRow: {
    flexDirection: "row",
    alignItems: "center",
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: 14,
    paddingHorizontal: 14,
    height: 58,
    backgroundColor: "#ffffff",
  },
  phoneRowFocused: {
    borderColor: colors.primary,
  },
  phoneRowError: {
    borderColor: colors.danger,
  },
  countryCode: {
    fontSize: 16,
    fontWeight: "800",
    color: colors.ink,
  },
  ccDivider: {
    width: 1,
    height: 24,
    backgroundColor: colors.border,
    marginHorizontal: 12,
  },
  phoneInput: {
    flex: 1,
    fontSize: 18,
    color: colors.ink,
    letterSpacing: 1.2,
    padding: 0,
  },
  hint: {
    fontSize: 11,
    color: colors.inkSoft,
    marginTop: 8,
    lineHeight: 16,
  },
  errorBox: {
    backgroundColor: colors.dangerSoft,
    borderRadius: 10,
    paddingVertical: 9,
    paddingHorizontal: 11,
    marginTop: 10,
  },
  errorText: {
    color: colors.danger,
    fontSize: 12,
    fontWeight: "600",
    lineHeight: 17,
  },
  noticeText: {
    color: colors.primaryDark,
    fontSize: 12,
    fontWeight: "600",
    marginTop: 10,
  },
  primaryBtn: {
    backgroundColor: colors.primary,
    borderRadius: 14,
    height: 54,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 18,
  },
  primaryBtnDisabled: {
    opacity: 0.45,
  },
  primaryBtnText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "800",
    letterSpacing: 0.2,
  },
  terms: {
    fontSize: 10,
    color: colors.inkSoft,
    textAlign: "center",
    lineHeight: 15,
    marginTop: 14,
  },
  changeBtn: {
    alignSelf: "flex-start",
    marginBottom: 14,
  },
  changeText: {
    fontSize: 13,
    fontWeight: "700",
    color: colors.primary,
  },
  phoneStrong: {
    fontWeight: "800",
    color: colors.ink,
  },
  otpRow: {
    flexDirection: "row",
    gap: 6,
    position: "relative",
    marginTop: 2,
  },
  otpHiddenInput: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    opacity: 0.01,
    color: "transparent",
    zIndex: 1,
  },
  otpCell: {
    flex: 1,
    height: 56,
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: "#fbfcfd",
    alignItems: "center",
    justifyContent: "center",
  },
  otpCellFilled: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  otpCellActive: {
    borderColor: colors.primary,
    backgroundColor: "#ffffff",
  },
  otpDigit: {
    fontSize: 22,
    fontWeight: "800",
    color: colors.ink,
  },
  demoCard: {
    backgroundColor: colors.amberSoft,
    borderRadius: 14,
    padding: 14,
    marginTop: 18,
    borderWidth: 1,
    borderColor: "#fde68a",
  },
  demoTop: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 8,
  },
  demoBadge: {
    backgroundColor: colors.amber,
    color: "#ffffff",
    fontSize: 9,
    fontWeight: "800",
    letterSpacing: 1,
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: 5,
    overflow: "hidden",
  },
  demoModeText: {
    fontSize: 10,
    color: "#92400e",
    fontWeight: "700",
  },
  demoText: {
    fontSize: 12,
    color: "#92400e",
    lineHeight: 17,
  },
  demoPhone: {
    fontWeight: "800",
  },
  demoCodeRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginTop: 8,
  },
  demoCode: {
    fontSize: 26,
    fontWeight: "800",
    letterSpacing: 6,
    color: "#78350f",
  },
  autofillBtn: {
    backgroundColor: "#ffffff",
    borderRadius: 8,
    paddingVertical: 7,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: "#fbbf24",
  },
  autofillText: {
    fontSize: 12,
    fontWeight: "800",
    color: "#92400e",
  },
  demoFoot: {
    fontSize: 10,
    color: "#a16207",
    marginTop: 8,
    lineHeight: 14,
  },
  resendRow: {
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center",
    gap: 5,
    marginTop: 16,
  },
  resendText: {
    fontSize: 12,
    color: colors.inkSoft,
  },
  resendAction: {
    fontSize: 12,
    fontWeight: "800",
    color: colors.primary,
  },
  resendDisabled: {
    color: colors.inkSoft,
    fontWeight: "600",
  },
  footer: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    flexWrap: "wrap",
    gap: 8,
    marginTop: 18,
    paddingHorizontal: 24,
  },
  footerItem: {
    fontSize: 10,
    color: colors.inkSoft,
    fontWeight: "600",
  },
  footerDot: {
    fontSize: 9,
    color: "#cbd5e1",
  },
});
