--
-- PostgreSQL database dump
--

\restrict MfMbSykx2ucfjSSawkcr5BeL6cKZlJ7XsiGJhnCZGZmVqt8qjH7uYGTn42ATSKl

-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: slide_pages; Type: TABLE; Schema: public; Owner: microcourse
--

CREATE TABLE public.slide_pages (
    id bigint NOT NULL,
    slide_id bigint NOT NULL,
    course_id bigint NOT NULL,
    page_number integer NOT NULL,
    image_url character varying(500) NOT NULL,
    thumbnail_url character varying(500),
    image_width integer,
    image_height integer,
    extracted_text text,
    has_animation boolean DEFAULT false NOT NULL,
    has_embedded_media boolean DEFAULT false NOT NULL,
    narration_script text,
    narration_audio_url character varying(500),
    audio_duration integer,
    narration_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    file_uuid character varying(64),
    chapter_id bigint,
    content_type character varying(20) DEFAULT 'PPT_RENDERED'::character varying NOT NULL,
    html_content text,
    section_id bigint,
    segment_count integer,
    voice character varying(64),
    tts_model character varying(64),
    generated_at timestamp without time zone,
    CONSTRAINT chk_slide_pages_content_type CHECK (((content_type)::text = ANY ((ARRAY['PPT_RENDERED'::character varying, 'HTML_DIRECT'::character varying])::text[])))
);


ALTER TABLE public.slide_pages OWNER TO microcourse;

--
-- Name: COLUMN slide_pages.segment_count; Type: COMMENT; Schema: public; Owner: microcourse
--

COMMENT ON COLUMN public.slide_pages.segment_count IS '该小节音频分段数量（如15段）';


--
-- Name: COLUMN slide_pages.voice; Type: COMMENT; Schema: public; Owner: microcourse
--

COMMENT ON COLUMN public.slide_pages.voice IS 'TTS 使用的音色 ID';


--
-- Name: COLUMN slide_pages.tts_model; Type: COMMENT; Schema: public; Owner: microcourse
--

COMMENT ON COLUMN public.slide_pages.tts_model IS 'TTS 模型（如 speech-2.8-hd）';


--
-- Name: COLUMN slide_pages.generated_at; Type: COMMENT; Schema: public; Owner: microcourse
--

COMMENT ON COLUMN public.slide_pages.generated_at IS '音频生成时间';


--
-- Name: slide_pages_id_seq; Type: SEQUENCE; Schema: public; Owner: microcourse
--

CREATE SEQUENCE public.slide_pages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.slide_pages_id_seq OWNER TO microcourse;

--
-- Name: slide_pages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: microcourse
--

ALTER SEQUENCE public.slide_pages_id_seq OWNED BY public.slide_pages.id;


--
-- Name: slide_pages id; Type: DEFAULT; Schema: public; Owner: microcourse
--

ALTER TABLE ONLY public.slide_pages ALTER COLUMN id SET DEFAULT nextval('public.slide_pages_id_seq'::regclass);


--
-- Name: slide_pages slide_pages_pkey; Type: CONSTRAINT; Schema: public; Owner: microcourse
--

ALTER TABLE ONLY public.slide_pages
    ADD CONSTRAINT slide_pages_pkey PRIMARY KEY (id);


--
-- Name: idx_slide_pages_chapter_id; Type: INDEX; Schema: public; Owner: microcourse
--

CREATE INDEX idx_slide_pages_chapter_id ON public.slide_pages USING btree (chapter_id);


--
-- Name: idx_slide_pages_content_type; Type: INDEX; Schema: public; Owner: microcourse
--

CREATE INDEX idx_slide_pages_content_type ON public.slide_pages USING btree (content_type);


--
-- Name: idx_slide_pages_narration_status; Type: INDEX; Schema: public; Owner: microcourse
--

CREATE INDEX idx_slide_pages_narration_status ON public.slide_pages USING btree (narration_status);


--
-- Name: idx_slide_pages_uuid; Type: INDEX; Schema: public; Owner: microcourse
--

CREATE INDEX idx_slide_pages_uuid ON public.slide_pages USING btree (file_uuid);


--
-- Name: idx_sp_narration_status; Type: INDEX; Schema: public; Owner: microcourse
--

CREATE INDEX idx_sp_narration_status ON public.slide_pages USING btree (narration_status);


--
-- Name: idx_sp_section; Type: INDEX; Schema: public; Owner: microcourse
--

CREATE INDEX idx_sp_section ON public.slide_pages USING btree (section_id);


--
-- Name: idx_sp_slide_id; Type: INDEX; Schema: public; Owner: microcourse
--

CREATE INDEX idx_sp_slide_id ON public.slide_pages USING btree (slide_id);


--
-- Name: uk_sp_course_section_page; Type: INDEX; Schema: public; Owner: microcourse
--

CREATE UNIQUE INDEX uk_sp_course_section_page ON public.slide_pages USING btree (course_id, section_id, page_number);


--
-- Name: slide_pages fk_sp_slide; Type: FK CONSTRAINT; Schema: public; Owner: microcourse
--

ALTER TABLE ONLY public.slide_pages
    ADD CONSTRAINT fk_sp_slide FOREIGN KEY (slide_id) REFERENCES public.course_slides(id) ON DELETE CASCADE;


--
-- Name: slide_pages slide_pages_course_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: microcourse
--

ALTER TABLE ONLY public.slide_pages
    ADD CONSTRAINT slide_pages_course_id_fkey FOREIGN KEY (course_id) REFERENCES public.courses(id);


--
-- PostgreSQL database dump complete
--

\unrestrict MfMbSykx2ucfjSSawkcr5BeL6cKZlJ7XsiGJhnCZGZmVqt8qjH7uYGTn42ATSKl

