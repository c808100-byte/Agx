const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

test("Unauthenticated user cannot read or write any Kashirak data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("products").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .set({
        userId: ALICE_UID,
        name: "Water",
        price: 2,
        cost: 1,
        stock: 40,
        expiryDate: "",
        deleted: false,
        clientUpdatedAt: Date.now(),
        createdAt: pastDate(),
        updatedAt: pastDate(),
      })
  );
});

test("Owner can create and update ShopSettings, but cannot tamper createdAt or inject ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      shopName: "جبر",
      ownerName: "احمد",
      phone: "0599000000",
      currency: "₪",
      cash0: 50,
      ownerPin: "1234",
      permItems: false,
      permReports: false,
      permDebts: true,
      permExpenses: false,
      permProfit: false,
      permCash: false,
      nextInvoiceNumber: 10,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      shopName: "سوبرماركت جبر",
      clientUpdatedAt: Date.now(),
      updatedAt: pastDate(1000),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );

  // Reject invalid PIN
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      ownerPin: "abc",
      updatedAt: pastDate(500),
    })
  );

  // Reject listing /users root
  await assertFails(aliceDb.collection("users").get());
});

test("Cross-user isolation prevents Bob from reading or writing Alice collections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = pastDate(5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .set({
        userId: ALICE_UID,
        name: "ماء 0.5 لتر",
        price: 2.0,
        cost: 1.0,
        stock: 40,
        expiryDate: "",
        deleted: false,
        clientUpdatedAt: Date.now(),
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .update({ stock: 99, updatedAt: pastDate(500) })
  );
});

test("Owner can CRUD products, customers, workers, sales, expenses, and ledgerEvents", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(5000);
  const base = aliceDb.collection("users").doc(ALICE_UID);

  // Product with barcode and minStock
  await assertSucceeds(
    base.collection("products").doc("p1").set({
      userId: ALICE_UID,
      name: "خبز",
      price: 3,
      cost: 2,
      stock: 25,
      minStock: 5,
      barcode: "6291041500213",
      expiryDate: "2026-12-01",
      deleted: false,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(base.collection("products").get());
  await assertSucceeds(
    base.collection("products").doc("p1").update({
      barcode: "6291041500999",
      minStock: 8,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    base.collection("products").doc("p1").update({
      minStock: -1,
      updatedAt: pastDate(900),
    })
  );

  // Customer
  await assertSucceeds(
    base.collection("customers").doc("c1").set({
      userId: ALICE_UID,
      name: "أبو أحمد",
      debt: 50,
      openingDebt: 50,
      sinceMillis: Date.now(),
      phone: "",
      note: "",
      deleted: false,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Worker
  await assertSucceeds(
    base.collection("workers").doc("w1").set({
      userId: ALICE_UID,
      name: "الكاشير",
      pin: "1111",
      deleted: false,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Sale
  await assertSucceeds(
    base.collection("sales").doc("s1").set({
      userId: ALICE_UID,
      invoiceNumber: 10,
      timestampMillis: Date.now(),
      total: 6,
      cost: 4,
      method: "cash",
      payName: "",
      customerId: "",
      customerName: "",
      items: [{ name: "خبز", qty: 2, price: 3, cost: 2 }],
      cashierName: "",
      deleted: false,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid sale payment method
  await assertFails(
    base.collection("sales").doc("s2").set({
      userId: ALICE_UID,
      invoiceNumber: 11,
      timestampMillis: Date.now(),
      total: 6,
      cost: 4,
      method: "crypto",
      payName: "",
      customerId: "",
      customerName: "",
      items: [],
      cashierName: "",
      deleted: false,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Expense
  await assertSucceeds(
    base.collection("expenses").doc("e1").set({
      userId: ALICE_UID,
      timestampMillis: Date.now(),
      title: "كهرباء",
      amount: 20,
      deleted: false,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // LedgerEvent
  await assertSucceeds(
    base.collection("ledgerEvents").doc("l1").set({
      userId: ALICE_UID,
      customerId: "c1",
      type: "payment",
      timestampMillis: Date.now(),
      amount: 15,
      note: "",
      deleted: false,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject ghost field on create
  await assertFails(
    base.collection("products").doc("p_bad").set({
      id: "p_bad",
      userId: ALICE_UID,
      name: "Extra ID",
      price: 1,
      cost: 0.5,
      stock: 1,
      expiryDate: "",
      deleted: false,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );
});
