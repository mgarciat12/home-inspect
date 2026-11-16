-- Local profile of each user, linked to the authorization server by the token "sub"
CREATE TABLE user_profile (
    id             BIGSERIAL PRIMARY KEY,
    subject        VARCHAR(100) NOT NULL UNIQUE,
    username       VARCHAR(100) NOT NULL,
    display_name   VARCHAR(150) NOT NULL,
    email          VARCHAR(255),
    role           VARCHAR(20)  NOT NULL,
    signature_path VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at  TIMESTAMPTZ
);

-- Company branding and standard texts used in the PDF (single row)
CREATE TABLE company_settings (
    id            SMALLINT PRIMARY KEY CHECK (id = 1),
    company_name  VARCHAR(200),
    logo_path     VARCHAR(500),
    address       VARCHAR(300),
    phone         VARCHAR(50),
    email         VARCHAR(255),
    website       VARCHAR(255),
    tax_id        VARCHAR(50),
    legal_text    TEXT,
    scope_text    TEXT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO company_settings (id, scope_text) VALUES (1,
'Esta inspección es un examen visual y no invasivo de las partes fácilmente accesibles de la vivienda, realizado conforme a un estándar de práctica profesional de inspección de viviendas (Standard of Practice). '
|| 'Su objetivo es identificar defectos materiales observables en la fecha de la inspección. No se desmontan elementos, no se mueven muebles ni objetos almacenados y no se realizan ensayos destructivos ni mediciones de laboratorio. '
|| 'El informe refleja el estado observado en el momento de la visita y no constituye una garantía, un seguro ni una certificación de cumplimiento normativo. '
|| 'Los elementos marcados como "Requiere evaluación por especialista" deben ser revisados por un profesional cualificado antes de tomar decisiones de compra o de obra.');
