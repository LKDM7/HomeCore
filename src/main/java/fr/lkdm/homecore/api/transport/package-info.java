/**
 * Bounded versioned client/server payloads and immutable typed wire values.
 * Decoders do not authorize operations or touch worlds. Server handlers must
 * authenticate the connection, validate permissions and execute on the server thread.
 * Snapshot NBT is server-to-client only; action parameters use a closed typed codec.
 */
package fr.lkdm.homecore.api.transport;
