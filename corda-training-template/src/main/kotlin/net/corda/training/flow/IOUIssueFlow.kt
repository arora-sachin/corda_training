package net.corda.training.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.training.contract.IOUContract
import net.corda.training.contract.IOUContract.Companion.IOU_CONTRACT_ID
import net.corda.training.state.IOUState
import java.time.ZoneId

/**
 * This is the flow which handles issuance of new IOUs on the ledger.
 * Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
 * Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
@InitiatingFlow
@StartableByRPC
class IOUIssueFlow(val state: IOUState) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
      val notary = serviceHub.networkMapCache.notaryIdentities[0]
      //  val notary =state.borrower

       println("borrower-----11--"+state.borrower);
        val txCommand = Command(IOUContract.Commands.Issue(), state.participants.map { it.owningKey })
        val txBuilder = TransactionBuilder(notary)  //TransactionBuilder is a factory class
                .addOutputState(state, IOU_CONTRACT_ID)
                .addCommand(txCommand)
        //verify
        txBuilder.verify(serviceHub)
        val partiallySignedTx = serviceHub.signInitialTransaction(txBuilder)
        val participantList = state.participants - ourIdentity

        val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
        val flowSession = participantList.map { initiateFlow(it) }.toSet()
        val collectSignaturesFlow = CollectSignaturesFlow(partiallySignedTx, flowSession)

        val fullySignedTx = subFlow(collectSignaturesFlow)

        return subFlow(FinalityFlow(fullySignedTx))
      //  return subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
        // Placeholder code to avoid type error when running the tests. Remove before starting the flow task!
        //return serviceHub.signInitialTransaction(txBuilder)
       // return fullySignedTx
    }
}

/**
 * This is the flow which signs IOU issuances.
 * The signing is handled by the [SignTransactionFlow].
 */
@InitiatedBy(IOUIssueFlow::class)
class IOUIssueFlowResponder(val flowSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction" using (output is IOUState)
            }
        }
        subFlow(signedTransactionFlow)
    }
}