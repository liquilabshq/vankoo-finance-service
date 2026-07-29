-- Tablas propias de Axon Framework (token store, saga store, dead-letter queue).
-- Axon Server es el event store; estas tablas viven en Postgres porque Axon las
-- gestiona vía JPA cuando hay un EntityManagerFactory disponible.
-- Estructura y tipos capturados generando el schema con Hibernate (ddl-auto=update)
-- una sola vez contra los @Entity reales de Axon 4.13.2, para que coincida
-- exactamente con lo que valida ddl-auto=validate. Los nombres de tabla siguen la
-- naming strategy del proyecto (snake_case + plural).

CREATE TABLE token_entries (
    processor_name character varying(255) NOT NULL,
    segment integer NOT NULL,
    owner character varying(255),
    "timestamp" character varying(255) NOT NULL,
    token oid,
    token_type character varying(255),
    CONSTRAINT token_entries_pkey PRIMARY KEY (processor_name, segment)
);

CREATE SEQUENCE saga_entries_seq START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE saga_entries (
    saga_id character varying(255) NOT NULL,
    revision character varying(255),
    saga_type character varying(255),
    serialized_saga oid,
    CONSTRAINT saga_entries_pkey PRIMARY KEY (saga_id)
);

CREATE SEQUENCE association_value_entries_seq START WITH 1 INCREMENT BY 50 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE association_value_entries (
    id bigint NOT NULL,
    association_key character varying(255) NOT NULL,
    association_value character varying(255),
    saga_id character varying(255) NOT NULL,
    saga_type character varying(255),
    CONSTRAINT association_value_entries_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_association_value_entries_saga_lookup
    ON association_value_entries (saga_type, association_key, association_value);

CREATE INDEX idx_association_value_entries_saga_id
    ON association_value_entries (saga_id, saga_type);

CREATE TABLE dead_letter_entries (
    dead_letter_id character varying(255) NOT NULL,
    cause_message character varying(1023),
    cause_type character varying(255),
    diagnostics oid,
    enqueued_at timestamp(6) with time zone NOT NULL,
    last_touched timestamp(6) with time zone,
    aggregate_identifier character varying(255),
    event_identifier character varying(255) NOT NULL,
    message_type character varying(255) NOT NULL,
    meta_data oid,
    payload oid NOT NULL,
    payload_revision character varying(255),
    payload_type character varying(255) NOT NULL,
    sequence_number bigint,
    time_stamp character varying(255) NOT NULL,
    token oid,
    token_type character varying(255),
    type character varying(255),
    processing_group character varying(255) NOT NULL,
    processing_started timestamp(6) with time zone,
    sequence_identifier character varying(255) NOT NULL,
    sequence_index bigint NOT NULL,
    CONSTRAINT dead_letter_entries_pkey PRIMARY KEY (dead_letter_id),
    CONSTRAINT uq_dead_letter_entries_sequence UNIQUE (processing_group, sequence_identifier, sequence_index)
);

CREATE INDEX idx_dead_letter_entries_sequence
    ON dead_letter_entries (processing_group, sequence_identifier);

CREATE INDEX idx_dead_letter_entries_processing_group
    ON dead_letter_entries (processing_group);
