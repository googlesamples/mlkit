/*
 * Copyright 2026 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mlkit.genai.demo.kotlin

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.google.mlkit.genai.agent.AgentTool
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Result returned when creating or listing notes via [BaseAgentDemoAppFunctionService].
 *
 * @property noteId Unique note identifier.
 * @property title Title of the note.
 * @property content Body text of the note.
 * @property status Summary status message.
 */
@AppFunctionSerializable(isDescribedByKDoc = false)
data class NoteResult(
  val noteId: String,
  val title: String,
  val content: String,
  val status: String,
)

/**
 * Result returned when creating or listing a reminder via [BaseAgentDemoAppFunctionService].
 *
 * @property reminderId Unique reminder identifier.
 * @property task Task description for the reminder.
 * @property dueTime Scheduled due time string.
 * @property status Summary status message.
 */
@AppFunctionSerializable(isDescribedByKDoc = false)
data class ReminderResult(
  val reminderId: String,
  val task: String,
  val dueTime: String,
  val status: String,
)

/**
 * Result returned when adjusting a smart home light via [BaseAgentDemoAppFunctionService].
 *
 * @property room Room name where the light is located.
 * @property isOn Whether the light is powered on.
 * @property brightnessPercent Brightness percentage (0 to 100).
 * @property status Summary status message.
 */
@AppFunctionSerializable(isDescribedByKDoc = false)
data class SmartLightResult(
  val room: String,
  val isOn: Boolean,
  val brightnessPercent: Int,
  val status: String,
)

/**
 * Result returned when recording a deposit or withdrawal on a bank account via
 * [BaseAgentDemoAppFunctionService].
 *
 * @property transactionId Unique transaction receipt ID.
 * @property account Target account name.
 * @property transactionType Transaction type (`"DEPOSIT"` or `"WITHDRAWAL"`).
 * @property amount Dollar amount deposited or withdrawn.
 * @property updatedBalance Updated balance of the account after the transaction.
 * @property status Summary status message.
 */
@AppFunctionSerializable(isDescribedByKDoc = false)
data class AccountTransactionResult(
  val transactionId: String,
  val account: String,
  val transactionType: String,
  val amount: Double,
  val updatedBalance: Double,
  val status: String,
)

/**
 * Result returned when transferring funds between accounts via [BaseAgentDemoAppFunctionService].
 *
 * @property transactionId Unique transaction receipt ID.
 * @property fromAccount Source account name.
 * @property toAccount Destination account name.
 * @property amountTransferred Dollar amount transferred.
 * @property remainingFromBalance Updated balance of the source account.
 * @property updatedToBalance Updated balance of the destination account.
 * @property status Summary status message.
 */
@AppFunctionSerializable(isDescribedByKDoc = false)
data class TransferFundsResult(
  val transactionId: String,
  val fromAccount: String,
  val toAccount: String,
  val amountTransferred: Double,
  val remainingFromBalance: Double,
  val updatedToBalance: Double,
  val status: String,
)

/**
 * Thread-safe in-memory sandbox data store backing the demo's `@AppFunctionDeclaration` tools.
 *
 * Pre-populated with sample notes, reminders, smart home lights, and bank account balances so that
 * single-turn and multi-turn agent tool calls inspect and mutate live state.
 */
object AgentDemoDataStore {
  private val lock = Any()
  private val nextId = AtomicInteger(100)

  data class NoteRecord(val id: String, val title: String, val content: String)

  data class ReminderRecord(val id: String, val task: String, val dueTime: String)

  data class LightRecord(val room: String, val isOn: Boolean, val brightnessPercent: Int)

  private val notes = mutableListOf<NoteRecord>()
  private val reminders = mutableListOf<ReminderRecord>()
  private val lights = linkedMapOf<String, LightRecord>()
  private val accounts = linkedMapOf<String, Double>()

  init {
    resetToDefaults()
  }

  /** Resets the in-memory sandbox store to its initial seed state. */
  fun resetToDefaults() {
    synchronized(lock) {
      nextId.set(100)
      notes.clear()
      notes.add(
        NoteRecord(
          id = "note-1",
          title = "Grocery List",
          content = "Oat milk, espresso beans, avocados, sourdough bread",
        )
      )
      notes.add(
        NoteRecord(
          id = "note-2",
          title = "Trip Packing",
          content = "Passport, USB-C charger, noise-canceling headphones",
        )
      )

      reminders.clear()
      reminders.add(
        ReminderRecord(
          id = "rem-1",
          task = "Submit Q3 project status update",
          dueTime = "Today at 4:00 PM",
        )
      )

      lights.clear()
      lights["living room"] = LightRecord("Living Room", isOn = true, brightnessPercent = 80)
      lights["bedroom"] = LightRecord("Bedroom", isOn = false, brightnessPercent = 0)
      lights["kitchen"] = LightRecord("Kitchen", isOn = true, brightnessPercent = 100)
      lights["office"] = LightRecord("Office", isOn = true, brightnessPercent = 65)

      accounts.clear()
      accounts["checking"] = 2450.00
      accounts["savings"] = 12800.00
      accounts["travel fund"] = 950.00
    }
  }

  fun createNote(title: String, content: String): NoteResult {
    synchronized(lock) {
      val id = "note-${nextId.incrementAndGet()}"
      val record = NoteRecord(id = id, title = title.trim(), content = content.trim())
      notes.add(record)
      return NoteResult(
        noteId = record.id,
        title = record.title,
        content = record.content,
        status = "Created note '${record.title}' (${record.id})",
      )
    }
  }

  fun listNotesSummary(): String {
    synchronized(lock) {
      if (notes.isEmpty()) {
        return "No notes found."
      }
      return notes.joinToString(separator = "\n") { note ->
        "- [${note.id}] ${note.title}: ${note.content}"
      }
    }
  }

  fun createReminder(task: String, dueTime: String): ReminderResult {
    synchronized(lock) {
      val id = "rem-${nextId.incrementAndGet()}"
      val record = ReminderRecord(id = id, task = task.trim(), dueTime = dueTime.trim())
      reminders.add(record)
      return ReminderResult(
        reminderId = record.id,
        task = record.task,
        dueTime = record.dueTime,
        status = "Scheduled reminder '${record.task}' for ${record.dueTime}",
      )
    }
  }

  fun listRemindersSummary(): String {
    synchronized(lock) {
      if (reminders.isEmpty()) {
        return "No reminders found."
      }
      return reminders.joinToString(separator = "\n") { reminder ->
        "- [${reminder.id}] ${reminder.task} (Due: ${reminder.dueTime})"
      }
    }
  }

  fun setDeviceLight(room: String, isOn: Boolean, brightnessPercent: Int): SmartLightResult {
    synchronized(lock) {
      val key = room.trim().lowercase(Locale.ROOT)
      val clampedBrightness = if (isOn) brightnessPercent.coerceIn(0, 100) else 0
      val displayName =
        lights[key]?.room
          ?: room.trim().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
          }
      val updated =
        LightRecord(room = displayName, isOn = isOn, brightnessPercent = clampedBrightness)
      lights[key] = updated
      val stateLabel = if (isOn) "ON (${clampedBrightness}%)" else "OFF"
      return SmartLightResult(
        room = updated.room,
        isOn = updated.isOn,
        brightnessPercent = updated.brightnessPercent,
        status = "Set ${updated.room} light to $stateLabel",
      )
    }
  }

  fun getAccountSummary(): String {
    synchronized(lock) {
      val balances =
        accounts.entries.joinToString(separator = ", ") { (name, balance) ->
          "${name.replaceFirstChar { it.titlecase(Locale.ROOT) }}: $${String.format(Locale.US, "%.2f", balance)}"
        }
      val lightsSummary =
        lights.values.joinToString(separator = ", ") { light ->
          "${light.room}: ${if (light.isOn) "ON (${light.brightnessPercent}%)" else "OFF"}"
        }
      return "Accounts: [$balances] | Smart Lights: [$lightsSummary] | Notes: ${notes.size} | Reminders: ${reminders.size}"
    }
  }

  fun depositFunds(account: String, amount: Double): AccountTransactionResult {
    synchronized(lock) {
      val accountKey = account.trim().lowercase(Locale.ROOT)
      val currentBalance = accounts[accountKey] ?: 0.0
      val txId = "tx-${nextId.incrementAndGet()}"
      if (amount <= 0.0) {
        return AccountTransactionResult(
          transactionId = txId,
          account = account,
          transactionType = "DEPOSIT",
          amount = 0.0,
          updatedBalance = currentBalance,
          status = "Rejected: Deposit amount must be positive.",
        )
      }
      val newBalance = currentBalance + amount
      accounts[accountKey] = newBalance
      return AccountTransactionResult(
        transactionId = txId,
        account = account,
        transactionType = "DEPOSIT",
        amount = amount,
        updatedBalance = newBalance,
        status =
          "Deposited $${String.format(Locale.US, "%.2f", amount)} into $account ($txId). New balance: $${String.format(Locale.US, "%.2f", newBalance)}.",
      )
    }
  }

  fun withdrawFunds(account: String, amount: Double): AccountTransactionResult {
    synchronized(lock) {
      val accountKey = account.trim().lowercase(Locale.ROOT)
      val currentBalance = accounts[accountKey] ?: 0.0
      val txId = "tx-${nextId.incrementAndGet()}"
      if (amount <= 0.0) {
        return AccountTransactionResult(
          transactionId = txId,
          account = account,
          transactionType = "WITHDRAWAL",
          amount = 0.0,
          updatedBalance = currentBalance,
          status = "Rejected: Withdrawal amount must be positive.",
        )
      }
      if (currentBalance < amount) {
        return AccountTransactionResult(
          transactionId = txId,
          account = account,
          transactionType = "WITHDRAWAL",
          amount = 0.0,
          updatedBalance = currentBalance,
          status =
            "Insufficient funds in $account (balance: $${String.format(Locale.US, "%.2f", currentBalance)}).",
        )
      }
      val newBalance = currentBalance - amount
      accounts[accountKey] = newBalance
      return AccountTransactionResult(
        transactionId = txId,
        account = account,
        transactionType = "WITHDRAWAL",
        amount = amount,
        updatedBalance = newBalance,
        status =
          "Withdrew $${String.format(Locale.US, "%.2f", amount)} from $account ($txId). New balance: $${String.format(Locale.US, "%.2f", newBalance)}.",
      )
    }
  }

  fun transferFunds(fromAccount: String, toAccount: String, amount: Double): TransferFundsResult {
    synchronized(lock) {
      val fromKey = fromAccount.trim().lowercase(Locale.ROOT)
      val toKey = toAccount.trim().lowercase(Locale.ROOT)
      val currentFrom = accounts[fromKey] ?: 0.0
      val currentTo = accounts[toKey] ?: 0.0
      val txId = "tx-${nextId.incrementAndGet()}"
      if (amount <= 0.0) {
        return TransferFundsResult(
          transactionId = txId,
          fromAccount = fromAccount,
          toAccount = toAccount,
          amountTransferred = 0.0,
          remainingFromBalance = currentFrom,
          updatedToBalance = currentTo,
          status = "Rejected: Transfer amount must be positive.",
        )
      }
      if (currentFrom < amount) {
        return TransferFundsResult(
          transactionId = txId,
          fromAccount = fromAccount,
          toAccount = toAccount,
          amountTransferred = 0.0,
          remainingFromBalance = currentFrom,
          updatedToBalance = currentTo,
          status =
            "Insufficient funds in $fromAccount (balance: $${String.format(Locale.US, "%.2f", currentFrom)}).",
        )
      }
      val newFrom = currentFrom - amount
      val newTo = currentTo + amount
      accounts[fromKey] = newFrom
      accounts[toKey] = newTo
      return TransferFundsResult(
        transactionId = txId,
        fromAccount = fromAccount,
        toAccount = toAccount,
        amountTransferred = amount,
        remainingFromBalance = newFrom,
        updatedToBalance = newTo,
        status =
          "Transferred $${String.format(Locale.US, "%.2f", amount)} from $fromAccount to $toAccount ($txId).",
      )
    }
  }

  /** Returns a formatted multi-line snapshot of all sandbox data for the Sandbox State viewer. */
  fun getFormattedSandboxSnapshot(): String {
    synchronized(lock) {
      return buildString {
          appendLine("=== Notes (${notes.size}) ===")
          notes.forEach { appendLine("• [${it.id}] ${it.title}: ${it.content}") }
          appendLine()
          appendLine("=== Reminders (${reminders.size}) ===")
          reminders.forEach { appendLine("• [${it.id}] ${it.task} (Due: ${it.dueTime})") }
          appendLine()
          appendLine("=== Smart Home Lights (${lights.size}) ===")
          lights.values.forEach {
            val state = if (it.isOn) "ON (${it.brightnessPercent}%)" else "OFF"
            appendLine("• ${it.room}: $state")
          }
          appendLine()
          appendLine("=== Bank Accounts (${accounts.size}) ===")
          accounts.forEach { (name, balance) ->
            val title = name.replaceFirstChar { it.titlecase(Locale.ROOT) }
            appendLine("• $title: $${String.format(Locale.US, "%.2f", balance)}")
          }
        }
        .trimEnd()
    }
  }

  /** Metadata descriptions of the built-in demo `@AppFunctionDeclaration` methods. */
  val BUILT_IN_FUNCTION_IDS: List<String> =
    listOf(
      "createNote",
      "listNotes",
      "createReminder",
      "listReminders",
      "setDeviceLight",
      "getAccountSummary",
      "depositFunds",
      "withdrawFunds",
      "transferFunds",
    )
}

/**
 * In-app [AppFunctionService] exposing productivity, smart home, and banking functions to the ML
 * Kit GenAI Agent API (`Agent.Builder.enableAppFunctions` and `AppFunctionAgentTools.discover`).
 *
 * The AndroidX AppFunctions compiler plugin generates `AgentDemoAppFunctionService` from this entry
 * point class along with `agent_demo_app_functions.xml` and `agent_demo_app_functions-v1.xml`.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
  serviceName = "AgentDemoAppFunctionService",
  appFunctionXmlFileName = "agent_demo_app_functions",
)
abstract class BaseAgentDemoAppFunctionService : AppFunctionService() {

  /**
   * Creates and saves a new note in the user's notebook.
   *
   * @param title Short title of the note.
   * @param content Full text body of the note.
   */
  @AppFunctionDeclaration(isDescribedByKDoc = false)
  suspend fun createNote(title: String, content: String): NoteResult =
    AgentDemoDataStore.createNote(title = title, content = content)

  /**
   * Lists all saved notes and their contents from the user's notebook.
   *
   * @param filterOptional Optional keyword filter; pass empty string to list all notes.
   */
  @AppFunctionDeclaration(isDescribedByKDoc = false)
  suspend fun listNotes(filterOptional: String): String {
    val all = AgentDemoDataStore.listNotesSummary()
    val query = filterOptional.trim()
    if (isUnfilteredQuery(query)) return all
    val filtered =
      all.lines().filter { it.contains(query, ignoreCase = true) }.joinToString(separator = "\n")
    return filtered.ifEmpty { "No notes matching '$query'." }
  }

  /**
   * Schedules a new task reminder for the user.
   *
   * @param task Description of what to remind the user about.
   * @param dueTime When the reminder should trigger (for example, "Tomorrow at 9:00 AM").
   */
  @AppFunctionDeclaration(isDescribedByKDoc = false)
  suspend fun createReminder(task: String, dueTime: String): ReminderResult =
    AgentDemoDataStore.createReminder(task = task, dueTime = dueTime)

  /**
   * Lists all scheduled task reminders and their due times for the user.
   *
   * @param filterOptional Optional keyword filter; pass empty string to list all reminders.
   */
  @AppFunctionDeclaration(isDescribedByKDoc = false)
  suspend fun listReminders(filterOptional: String): String {
    val all = AgentDemoDataStore.listRemindersSummary()
    val query = filterOptional.trim()
    if (isUnfilteredQuery(query)) return all
    val filtered =
      all.lines().filter { it.contains(query, ignoreCase = true) }.joinToString(separator = "\n")
    return filtered.ifEmpty { "No reminders matching '$query'." }
  }

  /**
   * Turns a smart home room light on or off and sets its brightness percentage.
   *
   * @param room Name of the room (such as "Living Room", "Bedroom", "Kitchen", or "Office").
   * @param isOn True to turn the light on, or false to turn it off.
   * @param brightnessPercent Brightness level from 0 to 100.
   */
  @AppFunctionDeclaration(isDescribedByKDoc = false)
  suspend fun setDeviceLight(
    room: String,
    isOn: Boolean,
    brightnessPercent: Int,
  ): SmartLightResult =
    AgentDemoDataStore.setDeviceLight(
      room = room,
      isOn = isOn,
      brightnessPercent = brightnessPercent,
    )

  /**
   * Returns a summary of the user's current bank account balances, smart home light states, and
   * note/reminder counts.
   *
   * @param includeDetails Pass true to include full balance and device status details.
   */
  @AppFunctionDeclaration(isDescribedByKDoc = false)
  suspend fun getAccountSummary(includeDetails: Boolean): String =
    AgentDemoDataStore.getAccountSummary()

  /**
   * Records a deposit into one of the user's bank accounts (such as "checking", "savings", or
   * "travel fund"), adding money to the account balance without transferring from another account.
   *
   * @param account Target account name (for example, "checking", "savings", or "travel fund").
   * @param amount Dollar amount to deposit into the account.
   */
  @AppFunctionDeclaration(isDescribedByKDoc = false)
  suspend fun depositFunds(account: String, amount: Double): AccountTransactionResult =
    AgentDemoDataStore.depositFunds(account = account, amount = amount)

  /**
   * Records a withdrawal from one of the user's bank accounts (such as "checking", "savings", or
   * "travel fund"), deducting money from the account balance without a destination account.
   *
   * @param account Source account name (for example, "checking", "savings", or "travel fund").
   * @param amount Dollar amount to withdraw from the account.
   */
  @AppFunctionDeclaration(isDescribedByKDoc = false)
  suspend fun withdrawFunds(account: String, amount: Double): AccountTransactionResult =
    AgentDemoDataStore.withdrawFunds(account = account, amount = amount)

  /**
   * Transfers money between two of the user's bank accounts (such as "checking", "savings", or
   * "travel fund").
   *
   * @param fromAccount Source account name (for example, "checking" or "savings").
   * @param toAccount Destination account name (for example, "checking", "savings", or "travel
   *   fund").
   * @param amount Dollar amount to transfer.
   */
  @AppFunctionDeclaration(isDescribedByKDoc = false)
  suspend fun transferFunds(
    fromAccount: String,
    toAccount: String,
    amount: Double,
  ): TransferFundsResult =
    AgentDemoDataStore.transferFunds(
      fromAccount = fromAccount,
      toAccount = toAccount,
      amount = amount,
    )

  private fun isUnfilteredQuery(query: String): Boolean =
    query.isEmpty() ||
      query.equals("all", ignoreCase = true) ||
      query.equals("*", ignoreCase = true) ||
      query.equals("none", ignoreCase = true) ||
      query.equals("null", ignoreCase = true)
}

/**
 * Decorator [AgentTool] demonstrating the Human-in-the-Loop (HITL) pattern for sensitive
 * AppFunction tools discovered via [com.google.mlkit.genai.agent.AppFunctionAgentTools.discover].
 *
 * Before delegating execution to [delegate], suspends to request explicit user approval via
 * [onRequireConfirmation]. If the user rejects the tool invocation, returns a structured
 * cancellation map to the model without executing the underlying AppFunction.
 */
class HitlConfirmationTool(
  delegate: AgentTool,
  private val onRequireConfirmation: suspend (toolName: String, args: Map<String, Any>) -> Boolean,
) : AgentTool(delegate) {

  override suspend fun execute(args: Map<String, Any>): Map<String, Any?> {
    val approved = onRequireConfirmation(name, args)
    return if (approved) {
      checkNotNull(delegate).execute(args)
    } else {
      mapOf(
        "status" to "cancelled_by_user",
        "tool" to name,
        "message" to "The user declined to approve execution of '$name' with arguments $args.",
      )
    }
  }
}
