import * as React from "react";
import { useState, useEffect } from "react";
import { Text, Select, FormContext } from "components/input";
import { Props as FilterFormProps } from "../filter-form";
import { Cancelable } from "utils/functions";

import { Template } from "./index";

type Client = {
  id: number;
  name: string;
  kernelId?: string;
};

type Product = {
  id: number;
  name: string;
};

type Kernel = {
  id: number;
  version: string;
  default?: boolean;
};

async function getClients(): Promise<Client[]> {
  return [
    {
      id: 1,
      name: "Placeholder Client 1",
    },
    {
      id: 2,
      name: "Placeholder Client 2",
    },
    {
      id: 3,
      name: "Placeholder Client 3",
    },
  ];
}

async function getProducts(): Promise<Product[]> {
  return [
    {
      id: 1,
      name: "SLES product 1",
    },
  ];
}

// TODO: Specify where and how this data realistically comes from
async function getKernels(...args: any[]): Promise<Kernel[]> {
  return [
    {
      id: 345,
      version: "1.2.3",
    },
    {
      id: 456,
      version: "1.2.4",
      default: true,
    },
  ];
}

export default (props: FilterFormProps & { template: Template }) => {
  const template = props.template;
  if (!template) {
    return null;
  }

  const formContext = React.useContext(FormContext);
  const setModelValue = formContext.setModelValue;
  const clientId = formContext.model.clientId;
  const productId = formContext.model.productId;
  const [clients, setClients] = useState<Client[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [kernels, setKernels] = useState<Kernel[]>([]);

  useEffect(() => {
    getClients().then(result => setClients(result));
    getProducts().then(result => setProducts(result));
  }, []);

  useEffect(() => {
    if (clientId) {
      // TODO: Clarify whether this is different for clients and products or same
      getKernels(clientId).then(result => {
        setKernels(result);

        const defaultKernel = result.find(item => Boolean(item.default));
        const kernelId = defaultKernel?.id ?? result[0]?.id ?? null;
        setModelValue?.("kernelId", kernelId);
      });
    } else if (productId) {
      getKernels(productId).then(result => {
        setKernels(result);

        const defaultKernel = result.find(item => Boolean(item.default));
        const kernelId = defaultKernel?.id ?? result[0]?.id ?? null;
        setModelValue?.("kernelId", kernelId);
      });
    } else {
      setKernels([]);
      setModelValue?.("kernelId", null);
    }
  }, [
    template, // If the template changes, reset what we previously had
    clientId,
    productId,
    setModelValue,
  ]);

  return (
    <>
      {template === Template.LivePatchingSystem && (
        <>
          <Select
            name="clientId"
            label={t("Client")}
            labelClass="col-md-3"
            divClass="col-md-6"
            required
            disabled={props.editing}
            options={clients}
            getOptionValue={client => client.id}
            getOptionLabel={client => client.name}
          />
        </>
      )}
      {template === Template.LivePatchingProduct && (
        <>
          <Select
            name="productId"
            label={t("Product")}
            labelClass="col-md-3"
            divClass="col-md-6"
            required
            disabled={props.editing}
            options={products}
            getOptionValue={product => product.id}
            getOptionLabel={product => product.name}
          />
        </>
      )}
      <Select
        name="kernelId"
        label={t("Kernel")}
        labelClass="col-md-3"
        divClass="col-md-6"
        required={!!(clientId || productId)}
        disabled={!clientId && !productId}
        options={kernels}
        getOptionValue={kernel => kernel.id}
        getOptionLabel={kernel => `${kernel.version}${kernel.default ? " (default)" : ""}`}
      />
    </>
  );
};
