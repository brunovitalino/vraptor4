/***
 * Copyright (c) 2009 Caelum - www.caelum.com.br/opensource
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package br.com.caelum.vraptor.validator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.MessageInterpolator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.caelum.vraptor.Result;
import br.com.caelum.vraptor.View;
import br.com.caelum.vraptor.proxy.Proxifier;
import br.com.caelum.vraptor.util.test.MockResult;
import br.com.caelum.vraptor.validator.beanvalidation.BeanValidatorContext;
import br.com.caelum.vraptor.view.ValidationViewsFactory;

/**
 * The default validator implementation.
 *
 * @author Guilherme Silveira
 */
@RequestScoped
public class DefaultValidator extends AbstractValidator {

	private static final Logger logger = LoggerFactory.getLogger(DefaultValidator.class);

	private final Result result;
	private final List<Message> errors = new ArrayList<>();
	private final ValidationViewsFactory viewsFactory;
	private final Outjector outjector;
	private final Proxifier proxifier;
	private final ResourceBundle bundle;

	private final javax.validation.Validator bvalidator;
	private final MessageInterpolator interpolator;
	private final Locale locale;

	/** 
	 * @deprecated CDI eyes only
	 */
	protected DefaultValidator() {
		this(null, null, null, null, null, null, null, null);
	}

	@Inject
	public DefaultValidator(Result result, ValidationViewsFactory factory, Outjector outjector, Proxifier proxifier, 
			ResourceBundle bundle, javax.validation.Validator bvalidator, MessageInterpolator interpolator, Locale locale) {
		this.result = result;
		this.viewsFactory = factory;
		this.outjector = outjector;
		this.proxifier = proxifier;
		this.bundle = bundle;
		this.bvalidator = bvalidator;
		this.interpolator = interpolator;
		this.locale = locale;
	}

	@Override
	public Validator check(boolean condition, Message message) {
		message.setBundle(bundle);
		if (!condition) {
			errors.add(message);
		}
		return this;
	}

	@Override
	public Validator validate(Object object, Class<?>... groups) {
		if (object != null) {
			addAll(bvalidator.validate(object, groups));
		}
		return this;
	}

	private void addAll(Set<ConstraintViolation<Object>> errors) {
		for (ConstraintViolation<Object> v : errors) {
			String msg = interpolator.interpolate(v.getMessageTemplate(), new BeanValidatorContext(v), locale);
			String category = v.getPropertyPath().toString();
			add(new SimpleMessage(category, msg));
			logger.debug("added message {}={} for contraint violation", category, msg);
		}
	}

	@Override
	public <T extends View> T onErrorUse(Class<T> view) {
		if (!hasErrors()) {
			return new MockResult(proxifier).use(view); //ignore anything, no errors occurred
		}

		result.include("errors", getErrors());
		outjector.outjectRequestMap();
		
		logger.debug("there are errors on result: {}", errors);
		return viewsFactory.instanceFor(view, errors);
	}

	@Override
	public void addAll(Collection<? extends Message> messages) {
		for (Message message : messages) {
			add(message);
		}
	}

	@Override
	public void add(Message message) {
		message.setBundle(bundle);
		errors.add(message);
	}

	@Override
	public boolean hasErrors() {
		return !errors.isEmpty();
	}

	@Override
	public List<Message> getErrors() {
		return new ErrorList(errors);
	}
}