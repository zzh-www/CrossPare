// Copyright 2015 Georg-August-Universit�t G�ttingen, Germany
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package de.ugoe.cs.cpdp.training;

import java.util.Random;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;

/**
 * Assigns a random class label to the instance it is evaluated on.
 * 
 * The range of class labels are hardcoded in fixedClassValues. This can later be extended to take
 * values from the XML configuration.
 */
public class RandomClass extends AbstractClassifier implements ITrainingStrategy,
    IWekaCompatibleTrainer
{

    private static final long serialVersionUID = 1L;

    private double[] fixedClassValues =
        { 0.0d, 1.0d };

    @Override
    public void setParameter(String parameters) {
        // do nothing, maybe take percentages for distribution later
    }

    @Override
    public void buildClassifier(Instances arg0) throws Exception {
        // do nothing
    }

    @Override
    public Classifier getClassifier() {
        return this;
    }

    @Override
    public void apply(Instances traindata) {
        // nothing to do
    }

    @Override
    public String getName() {
        return "RandomClass";
    }

    @Override
    public double classifyInstance(Instance instance) {
        Random rand = new Random();
        int randomNum = rand.nextInt(this.fixedClassValues.length);
        return this.fixedClassValues[randomNum];
    }
}