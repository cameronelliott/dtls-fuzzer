package se.uu.it.modeltester.execute;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.rub.nds.tlsattacker.core.protocol.message.HandshakeMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ProtocolMessage;
import de.rub.nds.tlsattacker.core.state.State;
import se.uu.it.modeltester.mutate.Mutation;

/**
 * A mutated input executor applies mutations at different stages in
 * sending an input. These stages coincide with those of the {@link MutatingInputExecutor}.
 * 
 * It server as a "replayer" for already generated mutations which produced 
 * interesting results.
 */
public class MutatedInputExecutor extends AbstractInputExecutor{
	
	private List<Mutation<FragmentationResult>> fragmentationMutations;
	private List<Mutation<PackingResult>> packingMutations;
	
	public MutatedInputExecutor(List<Mutation<?>> mutations) {
		// TODO bad design, we should at least make Mutated and Mutating Input Executor implementations be consistent
		fragmentationMutations = new LinkedList<>();
		packingMutations = new LinkedList<>();
		for (Mutation<?> mutation : mutations) {
			switch(mutation.getType()) {
			case FRAGMENT_REORDERING:
				fragmentationMutations.add((Mutation<FragmentationResult>) mutation);
				break;
			case MESSAGE_SPLITTING:
				fragmentationMutations.add((Mutation<FragmentationResult>) mutation);
				break;
			}
		}
	}

	@Override
	protected void sendMessage(ProtocolMessage message, State state) {
		ExecuteInputHelper helper = new ExecuteInputHelper();
		helper.prepareMessage(message, state);
		List<ProtocolMessage> messagesToSend = new LinkedList<>();

		if (message.isHandshakeMessage() && state.getTlsContext().getConfig().getDefaultSelectedProtocolVersion().isDTLS()) {
			FragmentationResult result = helper.fragmentMessage((HandshakeMessage) message, state);
			result = applyMutations(fragmentationMutations, result, state);
			messagesToSend.addAll(result.getFragments());
		} else {
			messagesToSend.add(message);
		}
		
		PackingResult result = helper.packMessages(messagesToSend, state);
		applyMutations(packingMutations, result, state);
		message.getHandler(state.getTlsContext()).adjustTlsContextAfterSerialize(message);
		helper.sendRecords(result.getRecords(), state);
	}
	
	private <R> R applyMutations(List<Mutation<R>> mutations, R result, State state)  {
		R mutatedResult = result;
		for (Mutation<R> mutation : mutations) {
			mutatedResult = mutation.mutate(result, state.getTlsContext());
		}
		return mutatedResult; 
	}
	
	public List<Mutation<?>> getMutations() {
		return Stream.concat(fragmentationMutations.stream(), packingMutations.stream())
				.collect(Collectors.toList());
	}
	
	public String getCompactMutationDescription() {
		return Stream.concat(fragmentationMutations.stream(), packingMutations.stream())
				.map(m -> m.toString())
				.reduce((s1,s2) -> s1 + "_" + s2).orElse("");
	}
}