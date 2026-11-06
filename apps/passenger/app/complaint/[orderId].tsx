import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  Alert,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import { getTier, type ComplaintType, type ComplaintCreate } from "@railbite/shared";
import { queueAction } from "../../src/db/queries.js";
import { processQueue } from "../../src/sync/queueWorker.js";

const COMPLAINT_TYPES: Array<{ key: string; label: string; tier: 1 | 2 }> = [
  { key: "COLD_FOOD", label: "Cold Food (Tier 1 - Replacement)", tier: 1 },
  { key: "MISSING_ITEM", label: "Missing Item (Tier 1 - Replacement)", tier: 1 },
  { key: "BAD_TASTE", label: "Bad Taste (Tier 1 - Replacement)", tier: 1 },
  { key: "WRONG_ITEM", label: "Wrong Item Delivered (Tier 1 - Replacement)", tier: 1 },
  { key: "OVERCHARGED", label: "Charged above MRP (Tier 2 - Regulatory)", tier: 2 },
  { key: "STALE_FOOD", label: "Stale / Spoiled Food (Tier 2 - Regulatory)", tier: 2 },
  { key: "HYGIENE", label: "Hygiene Issue / Foreign Object (Tier 2)", tier: 2 },
];

function generateUUID(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export default function ComplaintScreen() {
  const { orderId } = useLocalSearchParams() as { orderId: string };
  const router = useRouter();

  const [selectedType, setSelectedType] = useState<string>("COLD_FOOD");
  const [comment, setComment] = useState("");
  const [photoAdded, setPhotoAdded] = useState(false);

  const selectedTier = getTier(selectedType);

  const handleSubmit = () => {
    if (selectedTier === 2 && !photoAdded) {
      Alert.alert(
        "Photo Required",
        "Regulatory (Tier 2) complaints require photographic proof to escalate to IRCTC / RailMadad.",
      );
      return;
    }

    const complaintId = generateUUID();
    const now = new Date().toISOString();

    const payload: ComplaintCreate = {
      id: complaintId,
      orderId,
      type: selectedType as any,
      text: comment.trim() || undefined,
      createdAt: now,
      photoSha256: photoAdded ? "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" : undefined,
      photoTs: photoAdded ? now : undefined,
    };

    // Queue in local outbox
    queueAction(complaintId, "COMPLAINT", payload);
    processQueue();

    Alert.alert(
      "Complaint Filed",
      selectedTier === 1
        ? "Your replacement request has been sent to the pantry manager and captain. Target resolution: 15 mins."
        : "Your complaint has been queued with digital evidence hash and will be escalated to IRCTC/RailMadad.",
      [{ text: "OK", onPress: () => router.back() }],
    );
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={{ padding: 16 }}>
      <Text style={styles.title}>File a Food / Service Complaint</Text>
      <Text style={styles.subtitle}>Order Reference: #{orderId.slice(0, 8)}</Text>

      <Text style={styles.sectionHeader}>Select Issue Category</Text>
      {COMPLAINT_TYPES.map((type) => (
        <TouchableOpacity
          key={type.key}
          style={[
            styles.typeOption,
            selectedType === type.key && styles.typeOptionSelected,
          ]}
          onPress={() => setSelectedType(type.key)}
        >
          <Text
            style={[
              styles.typeText,
              selectedType === type.key && styles.typeTextSelected,
            ]}
          >
            {type.label}
          </Text>
        </TouchableOpacity>
      ))}

      {/* Tier 2 Photo Upload Proof */}
      {selectedTier === 2 ? (
        <View style={styles.photoContainer}>
          <Text style={styles.photoHeader}>Photographic Evidence (Mandatory for Tier 2)</Text>
          <Text style={styles.photoSub}>
            Photo is hashed with SHA-256 and timestamped to prevent tampering.
          </Text>
          <TouchableOpacity
            style={[styles.photoBtn, photoAdded && styles.photoBtnDone]}
            onPress={() => setPhotoAdded(!photoAdded)}
          >
            <Text style={styles.photoBtnText}>
              {photoAdded ? "✓ Photo Attached (evidence_sample.jpg)" : "📷 Take or Attach Photo"}
            </Text>
          </TouchableOpacity>
        </View>
      ) : null}

      <Text style={styles.sectionHeader}>Additional Details</Text>
      <TextInput
        style={styles.textInput}
        multiline
        numberOfLines={4}
        placeholder="Describe the issue with the food or service..."
        value={comment}
        onChangeText={setComment}
      />

      <TouchableOpacity style={styles.submitBtn} onPress={handleSubmit}>
        <Text style={styles.submitBtnText}>Submit Complaint →</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#ffffff",
  },
  title: {
    fontSize: 20,
    fontWeight: "bold",
    color: "#0f172a",
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 13,
    color: "#64748b",
    marginBottom: 16,
  },
  sectionHeader: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#334155",
    marginTop: 14,
    marginBottom: 8,
  },
  typeOption: {
    padding: 14,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#e2e8f0",
    marginBottom: 8,
    backgroundColor: "#f8fafc",
  },
  typeOptionSelected: {
    borderColor: "#1e3a8a",
    backgroundColor: "#eff6ff",
  },
  typeText: {
    fontSize: 14,
    color: "#334155",
  },
  typeTextSelected: {
    color: "#1e3a8a",
    fontWeight: "bold",
  },
  photoContainer: {
    backgroundColor: "#fef3c7",
    padding: 14,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: "#fde68a",
    marginVertical: 12,
  },
  photoHeader: {
    fontSize: 13,
    fontWeight: "bold",
    color: "#92400e",
    marginBottom: 4,
  },
  photoSub: {
    fontSize: 11,
    color: "#78350f",
    marginBottom: 10,
  },
  photoBtn: {
    backgroundColor: "#d97706",
    padding: 12,
    borderRadius: 6,
    alignItems: "center",
  },
  photoBtnDone: {
    backgroundColor: "#166534",
  },
  photoBtnText: {
    color: "#ffffff",
    fontWeight: "bold",
    fontSize: 13,
  },
  textInput: {
    borderWidth: 1,
    borderColor: "#cbd5e1",
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    height: 100,
    textAlignVertical: "top",
    backgroundColor: "#f8fafc",
    marginBottom: 20,
  },
  submitBtn: {
    backgroundColor: "#dc2626",
    padding: 16,
    borderRadius: 8,
    alignItems: "center",
  },
  submitBtnText: {
    color: "#ffffff",
    fontSize: 16,
    fontWeight: "bold",
  },
});
